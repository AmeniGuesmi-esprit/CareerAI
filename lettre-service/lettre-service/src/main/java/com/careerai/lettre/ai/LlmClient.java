package com.careerai.lettre.ai;

import com.careerai.lettre.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientException;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Client générique pour les API « chat/completions » compatibles OpenAI (Mistral, Cohere...).
 * Le fournisseur est choisi par son nom ; URL, clé et modèle viennent de application.properties.
 * En cas d'erreur 429 (limite du plan gratuit), l'appel est retenté automatiquement avec une courte pause.
 */
@Slf4j
@Component
public class LlmClient {

    /** Résultat d'un appel : texte généré + fournisseur et modèle réellement utilisés. */
    public record Result(String text, String provider, String model) {}

    private static final int MAX_RETRIES_ON_429 = 2;
    private static final long MAX_WAIT_MS = 8000;

    private final LlmProperties props;
    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    // Cache mémoire borné (LRU + TTL) : évite de refacturer une requête identique.
    private final long cacheTtlMs;
    private final int cacheMaxEntries;
    private final Map<String, CachedResult> responseCache = new LinkedHashMap<>(16, .75f, true);

    // Métriques depuis le démarrage (ou le dernier reset). Latences : derniers échantillons par fournisseur.
    static final int LATENCY_SAMPLES = 1000;
    private final Map<String, LongAdder> providerCalls = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> providerFailures = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> providerLatencyMs = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> providerLatencySamples = new ConcurrentHashMap<>();
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheMisses = new LongAdder();
    private final LongAdder cacheHitNanos = new LongAdder();
    private final LongAdder successfulCalls = new LongAdder();
    private final LongAdder failedCalls = new LongAdder();
    private volatile long metricsSince = System.currentTimeMillis();

    private record CachedResult(Result result, long createdAt) {}

    public LlmClient(LlmProperties props) {
        this.props = props;
        this.cacheTtlMs = Math.max(0, props.getCacheTtlSeconds()) * 1000L;
        this.cacheMaxEntries = Math.max(1, props.getCacheMaxEntries());
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public String resolveName(String requested) {
        return requested == null || requested.isBlank()
                ? props.getDefaultProvider()
                : requested.trim().toLowerCase(Locale.ROOT);
    }

    public Result chat(String providerName, String system, String user) {
        return chat(providerName, system, user, true);
    }

    /** Utilisé pour les benchmarks afin que les mesures ne soient pas faussées par le cache. */
    public Result chat(String providerName, String system, String user, boolean useCache) {
        String name = resolveName(providerName);
        useCache = useCache && cacheTtlMs > 0;
        String key = cacheKey(name, system, user);
        if (useCache) {
            long lookupStart = System.nanoTime();
            synchronized (responseCache) {
                CachedResult cached = responseCache.get(key);
                if (cached != null && System.currentTimeMillis() - cached.createdAt() < cacheTtlMs) {
                    cacheHits.increment();
                    cacheHitNanos.add(System.nanoTime() - lookupStart);
                    return cached.result();
                }
                if (cached != null) responseCache.remove(key);
            }
            cacheMisses.increment();
        }
        long startedAt = System.nanoTime();
        providerCalls.computeIfAbsent(name, k -> new LongAdder()).increment();
        try {
            LlmProperties.Provider p = props.getProviders().get(name);
            if (p == null) {
                throw ApiException.badRequest("Fournisseur IA inconnu : " + name);
            }
            if (!p.isConfigured()) {
                throw ApiException.unavailable("Le fournisseur « " + name + " » n'est pas configuré (clé API manquante).");
            }

            Map<String, Object> body = new HashMap<>();
            body.put("model", p.getModel());
            body.put("temperature", props.getTemperature());
            body.put("max_tokens", props.getMaxTokens());
            body.put("messages", List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", user)));
            if (p.getReasoningEffort() != null && !p.getReasoningEffort().isBlank()) {
                body.put("reasoning_effort", p.getReasoningEffort().trim());
            }

            JsonNode res;
            for (int attempt = 0; ; attempt++) {
                try {
                    res = restClient.post()
                            .uri(p.getUrl())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + p.getApiKey())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(JsonNode.class);
                    break;
                } catch (RestClientResponseException e) {
                    int status = e.getStatusCode().value();
                    if (status == 429 && attempt < MAX_RETRIES_ON_429) {
                        long wait = waitMillis(e, attempt);
                        log.info("LLM {} : 429, nouvel essai dans {} ms ({}/{})", name, wait, attempt + 1, MAX_RETRIES_ON_429);
                        sleep(wait);
                        continue;
                    }
                    throw mapHttpError(name, e);
                } catch (RestClientException e) {
                    throw mapClientError(name, e);
                }
            }

            JsonNode choice = res == null ? null : res.path("choices").path(0);
            String text = choice == null ? "" : choice.path("message").path("content").asText("");
            if (text.isBlank()) {
                throw ApiException.badGateway("Le fournisseur « " + name + " » a renvoyé une réponse vide.");
            }
            if ("length".equals(choice.path("finish_reason").asText(""))) {
                log.warn("LLM {} : réponse tronquée (max_tokens atteint)", name);
                throw ApiException.badGateway("La réponse de « " + name + " » a été coupée (trop longue). Réessayez ou changez de modèle IA.");
            }
            Result result = new Result(text, name, p.getModel());
            if (useCache) {
                synchronized (responseCache) {
                    responseCache.put(key, new CachedResult(result, System.currentTimeMillis()));
                    while (responseCache.size() > cacheMaxEntries) {
                        responseCache.remove(responseCache.keySet().iterator().next());
                    }
                }
            }
            successfulCalls.increment();
            return result;
        } catch (RuntimeException e) {
            failedCalls.increment();
            providerFailures.computeIfAbsent(name, k -> new LongAdder()).increment();
            throw e;
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
            providerLatencyMs.computeIfAbsent(name, k -> new LongAdder()).add(elapsedMs);
            Deque<Long> samples = providerLatencySamples.computeIfAbsent(name, k -> new ArrayDeque<>());
            synchronized (samples) {
                samples.addLast(elapsedMs);
                while (samples.size() > LATENCY_SAMPLES) samples.pollFirst();
            }
        }
    }

    /** Nombre de réponses actuellement en cache. */
    public int cacheSize() {
        synchronized (responseCache) {
            return responseCache.size();
        }
    }

    /** Remet les compteurs à zéro (avant une campagne de mesure) ; le cache est aussi vidé. */
    public void resetMetrics() {
        synchronized (responseCache) {
            responseCache.clear();
        }
        providerCalls.clear();
        providerFailures.clear();
        providerLatencyMs.clear();
        providerLatencySamples.clear();
        cacheHits.reset();
        cacheMisses.reset();
        cacheHitNanos.reset();
        successfulCalls.reset();
        failedCalls.reset();
        metricsSince = System.currentTimeMillis();
    }

    /** Percentile par la méthode du rang le plus proche (p entre 0 et 100). Liste vide = 0. */
    public static long percentile(List<Long> values, double p) {
        if (values == null || values.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        return sorted.get(Math.min(sorted.size() - 1, Math.max(0, rank - 1)));
    }

    public Map<String, Object> metrics() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("since", java.time.Instant.ofEpochMilli(metricsSince).toString());
        out.put("successfulCalls", successfulCalls.sum());
        out.put("failedCalls", failedCalls.sum());
        long hits = cacheHits.sum();
        long misses = cacheMisses.sum();
        out.put("cacheHits", hits);
        out.put("cacheMisses", misses);
        long total = hits + misses;
        out.put("cacheHitRatePercent", total == 0 ? 0.0 : round2(hits * 100.0 / total));
        out.put("cacheHitAverageLatencyMs", hits == 0 ? 0.0 : round2(cacheHitNanos.sum() / 1_000_000.0 / hits));
        out.put("cacheSize", cacheSize());
        out.put("cacheTtlSeconds", cacheTtlMs / 1000);
        out.put("cacheMaxEntries", cacheMaxEntries);

        Map<String, Object> providers = new LinkedHashMap<>();
        providerCalls.forEach((name, calls) -> {
            long count = calls.sum();
            long failures = providerFailures.getOrDefault(name, new LongAdder()).sum();
            List<Long> samples;
            Deque<Long> d = providerLatencySamples.getOrDefault(name, new ArrayDeque<>());
            synchronized (d) {
                samples = new ArrayList<>(d);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("calls", count);
            m.put("failures", failures);
            m.put("successRatePercent", count == 0 ? 0.0 : round2((count - failures) * 100.0 / count));
            m.put("averageLatencyMs", count == 0 ? 0 : providerLatencyMs.getOrDefault(name, new LongAdder()).sum() / count);
            m.put("p50LatencyMs", percentile(samples, 50));
            m.put("p95LatencyMs", percentile(samples, 95));
            m.put("minLatencyMs", samples.isEmpty() ? 0 : Collections.min(samples));
            m.put("maxLatencyMs", samples.isEmpty() ? 0 : Collections.max(samples));
            providers.put(name, m);
        });
        out.put("providers", providers);
        return out;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String cacheKey(String provider, String system, String user) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((provider + "|" + system + "|" + user).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    // ───────────────────────── Erreurs ─────────────────────────

    private ApiException mapHttpError(String name, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String body = e.getResponseBodyAsString();
        log.warn("LLM {} a répondu {} : {}", name, status, body.length() > 400 ? body.substring(0, 400) + "…" : body);
        String detail = providerMessage(body);
        String suffix = detail.isEmpty() ? "" : " Détail : " + detail;

        if (status == 401 || status == 403) {
            return ApiException.badGateway("Clé API refusée par « " + name + " » : vérifiez-la dans la configuration." + suffix);
        }
        if (status == 429) {
            return ApiException.tooManyRequests("« " + name + " » est saturé ou sa limite gratuite est atteinte. Réessayez dans une minute ou changez de modèle IA." + suffix);
        }
        return ApiException.badGateway("Le fournisseur « " + name + " » a refusé la requête (HTTP " + status + ")." + suffix);
    }

    private ApiException mapClientError(String name, RestClientException e) {
        if (isTimeout(e)) {
            return ApiException.gatewayTimeout("Le fournisseur « " + name + " » met trop de temps à répondre.");
        }
        if (e instanceof ResourceAccessException) {
            log.warn("LLM {} injoignable : {}", name, e.getMessage());
            return ApiException.badGateway("Impossible de joindre le fournisseur « " + name + " ».");
        }
        log.warn("LLM {} : réponse illisible : {}", name, e.getMessage());
        return ApiException.badGateway("Réponse illisible du fournisseur « " + name + " ». Réessayez ou changez de modèle IA.");
    }

    private static boolean isTimeout(Throwable e) {
        Throwable c = e;
        for (int depth = 0; c != null && depth < 10; depth++, c = c.getCause()) {
            if (c instanceof HttpTimeoutException || c instanceof SocketTimeoutException || c instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    /** Message d'erreur lisible renvoyé par le fournisseur (champ « message » ou « error.message »). */
    private String providerMessage(String body) {
        try {
            JsonNode n = mapper.readTree(body);
            String m = n.path("message").isTextual() ? n.path("message").asText() : n.path("error").path("message").asText("");
            m = m.replaceAll("\\s+", " ").strip();
            return m.length() > 160 ? m.substring(0, 160) : m;
        } catch (Exception ex) {
            return "";
        }
    }

    private long waitMillis(RestClientResponseException e, int attempt) {
        String retryAfter = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After");
        try {
            if (retryAfter != null) return Math.min(MAX_WAIT_MS, Long.parseLong(retryAfter.trim()) * 1000);
        } catch (NumberFormatException ignored) { /* on retombe sur le délai par défaut */ }
        return Math.min(MAX_WAIT_MS, 2000L * (attempt + 1));
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ApiException.gatewayTimeout("Requête interrompue.");
        }
    }
}