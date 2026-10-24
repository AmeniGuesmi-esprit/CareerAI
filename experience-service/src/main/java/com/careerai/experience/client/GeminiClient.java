package com.careerai.experience.client;

import com.careerai.experience.exception.GeminiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Client de l'API Gemini (Google AI Studio).
 * La cle est lue depuis la propriete gemini.api-key (variable d'environnement GEMINI_API_KEY)
 * et transmise dans l'en-tete x-goog-api-key : elle n'apparait ni dans l'URL ni dans les logs.
 * Chaque reponse reussie est journalisee avec sa consommation de jetons (usageMetadata)
 * et enregistree dans LlmCallStats lorsqu'une collecte est active.
 */
@Slf4j
@Component
public class GeminiClient {

    private static final String GENERATE_PATH = "/models/{model}:generateContent";
    private static final String API_KEY_HEADER = "x-goog-api-key";
    private static final int MAX_ERROR_BODY_LENGTH = 500;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final int maxAttempts;
    private final long initialBackoffMs;

    public GeminiClient(
            ObjectMapper objectMapper,
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${gemini.model:gemini-2.5-flash}") String model,
            @Value("${gemini.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${gemini.read-timeout-ms:120000}") int readTimeoutMs,
            @Value("${gemini.max-attempts:3}") int maxAttempts,
            @Value("${gemini.initial-backoff-ms:1000}") long initialBackoffMs) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoffMs = Math.max(0, initialBackoffMs);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Appelle Gemini et retourne le JSON genere (String).
     *
     * @param systemPrompt instruction systeme (peut etre null ou vide)
     * @param userPrompt   prompt utilisateur
     * @param temperature  temperature de generation
     * @param jsonSchema   schema JSON de la reponse (String, peut etre null ou vide)
     */
    public String generate(String systemPrompt, String userPrompt, double temperature, String jsonSchema) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GeminiException("GEMINI_API_KEY n'est pas configuree");
        }

        Map<String, Object> body = buildBody(systemPrompt, userPrompt, temperature, jsonSchema);
        long startNanos = System.nanoTime();

        for (int attempt = 1; ; attempt++) {
            try {
                // lecture en String : accepte n'importe quel Content-Type (ex. application/octet-stream),
                // le JSON est parse ensuite par nos soins avec un message d'erreur exploitable
                String raw = restClient.post()
                        .uri(GENERATE_PATH, model)
                        .header(API_KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                JsonNode response = parseResponse(raw);
                // les jetons sont comptes meme si la reponse est ensuite jugee inexploitable
                recordUsage(temperature, response, attempt, startNanos);
                return extractText(response);

            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (!isRetryable(status)) {
                    throw new GeminiException("Gemini a repondu HTTP " + status + " : "
                            + truncate(e.getResponseBodyAsString()), e);
                }
                if (attempt >= maxAttempts) {
                    throw new GeminiException("Gemini indisponible apres " + attempt
                            + " tentatives (dernier statut HTTP " + status + ")", e);
                }
                log.warn("Gemini HTTP {} - tentative {}/{}", status, attempt, maxAttempts);

            } catch (ResourceAccessException e) {
                // timeout de connexion / de lecture, erreur reseau
                if (attempt >= maxAttempts) {
                    throw new GeminiException("Gemini injoignable apres " + attempt + " tentatives", e);
                }
                log.warn("Gemini erreur reseau/timeout ({}) - tentative {}/{}",
                        e.getClass().getSimpleName(), attempt, maxAttempts);

            } catch (TransientResponseException e) {
                // 200 mais corps vide / non JSON (coupure, proxy, page d'erreur) : on retente
                if (attempt >= maxAttempts) {
                    throw new GeminiException("Reponse Gemini inexploitable apres " + attempt
                            + " tentatives : " + e.getMessage(), e);
                }
                log.warn("Gemini reponse inexploitable ({}) - tentative {}/{}", e.getMessage(), attempt, maxAttempts);

            } catch (RestClientException e) {
                // Spring enveloppe ici les IOException survenues PENDANT la lecture du corps
                // (timeout de lecture, connexion coupee) : ce n'est pas une ResourceAccessException.
                // Ce sont des erreurs transitoires -> retry ; le reste (decodage...) est definitif.
                if (!hasCause(e, IOException.class)) {
                    throw new GeminiException("Erreur lors de la lecture de la reponse Gemini : " + e.getMessage(), e);
                }
                if (attempt >= maxAttempts) {
                    throw new GeminiException("Gemini : lecture de la reponse impossible apres " + attempt
                            + " tentatives (" + rootCauseSummary(e) + ")", e);
                }
                log.warn("Gemini erreur de lecture de la reponse ({}) - tentative {}/{}",
                        rootCauseSummary(e), attempt, maxAttempts);
            }

            backoff(attempt);
        }
    }

    // ---------- helpers ----------

    /** Erreur interne : reponse HTTP 200 inexploitable (corps vide ou non JSON), consideree comme transitoire. */
    private static final class TransientResponseException extends RuntimeException {
        TransientResponseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private JsonNode parseResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new TransientResponseException("corps de reponse vide", null);
        }
        try {
            return objectMapper.readTree(raw);
        } catch (IOException e) {
            throw new TransientResponseException("corps non JSON : " + truncate(raw), e);
        }
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }

    private static String rootCauseSummary(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : " : " + root.getMessage());
    }

    /** Lit usageMetadata (jetons), journalise la consommation et alimente LlmCallStats. */
    private void recordUsage(double temperature, JsonNode response, int httpAttempts, long startNanos) {
        JsonNode usage = response == null ? null : response.path("usageMetadata");
        int prompt = usage == null ? 0 : usage.path("promptTokenCount").asInt(0);
        int output = usage == null ? 0 : usage.path("candidatesTokenCount").asInt(0);
        int thoughts = usage == null ? 0 : usage.path("thoughtsTokenCount").asInt(0);
        int total = usage == null ? 0 : usage.path("totalTokenCount").asInt(0);
        if (total == 0) {
            total = prompt + output + thoughts;
        }
        long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        log.info("Gemini : modele={} jetons(prompt={}, sortie={}, reflexion={}, total={}) en {} ms, {} tentative(s) HTTP",
                model, prompt, output, thoughts, total, latencyMs, httpAttempts);
        LlmCallStats.record(new LlmCallStats.Call(
                temperature, latencyMs, prompt, output, thoughts, total, httpAttempts));
    }

    private Map<String, Object> buildBody(String systemPrompt, String userPrompt, double temperature, String jsonSchema) {
        Map<String, Object> generationConfig = new java.util.LinkedHashMap<>();
        generationConfig.put("temperature", temperature);
        generationConfig.put("responseMimeType", "application/json");
        if (jsonSchema != null && !jsonSchema.isBlank()) {
            generationConfig.put("responseSchema", parseSchema(jsonSchema));
        }

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        }
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))));
        body.put("generationConfig", generationConfig);
        return body;
    }

    private JsonNode parseSchema(String jsonSchema) {
        try {
            return objectMapper.readTree(jsonSchema);
        } catch (Exception e) {
            throw new GeminiException("Schema JSON invalide", e);
        }
    }

    private String extractText(JsonNode response) {
        if (response == null) {
            throw new GeminiException("Reponse Gemini vide");
        }
        JsonNode candidate = response.path("candidates").path(0);
        if (candidate.isMissingNode()) {
            String blockReason = response.path("promptFeedback").path("blockReason").asText("");
            throw new GeminiException(blockReason.isEmpty()
                    ? "Reponse Gemini sans candidat"
                    : "Prompt bloque par Gemini : " + blockReason);
        }
        JsonNode textNode = candidate.path("content").path("parts").path(0).path("text");
        if (textNode.isMissingNode() || textNode.asText().isBlank()) {
            throw new GeminiException("Reponse Gemini sans contenu (finishReason="
                    + candidate.path("finishReason").asText("inconnu") + ")");
        }
        return textNode.asText();
    }

    private boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    /** Backoff exponentiel : base * 2^(tentative-1), avec un leger jitter. */
    private void backoff(int attempt) {
        long delay = initialBackoffMs * (1L << (attempt - 1));
        long jitter = delay > 0 ? ThreadLocalRandom.current().nextLong(delay / 4 + 1) : 0;
        try {
            Thread.sleep(delay + jitter);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new GeminiException("Appel Gemini interrompu", ie);
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= MAX_ERROR_BODY_LENGTH ? s : s.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
    }
}