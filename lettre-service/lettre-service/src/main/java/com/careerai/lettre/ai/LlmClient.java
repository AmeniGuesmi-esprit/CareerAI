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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    public LlmClient(LlmProperties props) {
        this.props = props;
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
        String name = resolveName(providerName);
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
        return new Result(text, name, p.getModel());
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