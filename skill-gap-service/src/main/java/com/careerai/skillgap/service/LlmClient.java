package com.careerai.skillgap.service;

import com.careerai.skillgap.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client LLM compatible "OpenAI Chat Completions" (Gemini, Groq, OpenRouter, Ollama...).
 */
@Slf4j
@Component
public class LlmClient {

    private final ObjectMapper mapper;
    private final RestClient http;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final int maxTokens;
    private final String reasoningEffort;

    public LlmClient(ObjectMapper mapper,
                        @Value("${llm.api-url}") String apiUrl,
                        @Value("${llm.api-key:}") String apiKey,
                        @Value("${llm.model}") String model,
                        @Value("${llm.max-tokens}") int maxTokens,
                        @Value("${llm.reasoning-effort:}") String reasoningEffort,
                        @Value("${llm.timeout-seconds}") int timeoutSeconds) {
        this.mapper = mapper;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.reasoningEffort = reasoningEffort;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(timeoutSeconds * 1000);
        this.http = RestClient.builder().baseUrl(apiUrl).requestFactory(factory).build();
    }

    public String model() {
        return model;
    }

    /** Réponse du modèle avec ses mesures : latence de l'appel et jetons consommés (champ usage de l'API). */
    public record Completion(String text, long latencyMs, int promptTokens, int completionTokens) {
        public int totalTokens() {
            return promptTokens + completionTokens;
        }
    }

    /** Retourne le texte de la réponse du modèle. */
    public String complete(String system, String user) {
        return call(system, user).text();
    }

    /** Appelle le modèle et mesure la latence et les jetons (journalisés, jamais le contenu du CV). */
    public Completion call(String system, String user) {
        boolean local = apiUrl.contains("localhost") || apiUrl.contains("127.0.0.1");
        if (!local && (apiKey == null || apiKey.isBlank())) {
            throw ApiException.unavailable("Service d'analyse indisponible : clé API du LLM absente (LLM_API_KEY).");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);   // Groq le compte dans la limite TPM : le garder bas
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            body.put("reasoning_effort", reasoningEffort);   // low | medium | high (modèles gpt-oss)
        }

        String raw;
        long start = System.nanoTime();
        try {
            RestClient.RequestBodySpec req = http.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) {
                req = req.header("Authorization", "Bearer " + apiKey);
            }
            raw = req.body(body).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            throw mapHttpError(e);
        } catch (RestClientException e) {
            // Selon le moment où il survient, un timeout arrive en ResourceAccessException (connexion)
            // ou en RestClientException (lecture de la réponse) : on cherche la cause réelle.
            if (isTimeout(e)) {
                log.warn("Appel LLM : timeout : {}", e.getMessage());
                throw ApiException.gatewayTimeout("Le service IA ne répond pas (timeout). Réessayez dans un instant.");
            }
            log.warn("Appel LLM : erreur réseau : {}", e.getMessage());
            throw ApiException.unavailable("Service IA injoignable. Vérifiez la connexion et réessayez.");
        }

        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode choice = root.path("choices").path(0);
            if ("length".equals(choice.path("finish_reason").asText())) {
                throw ApiException.badGateway("La réponse de l'IA a été tronquée. Réessayez avec une offre plus courte.");
            }
            String text = choice.path("message").path("content").asText("");
            if (text.isBlank()) {
                throw ApiException.badGateway("Réponse vide du service IA. Réessayez.");
            }
            JsonNode usage = root.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);
            log.info("Appel LLM : modèle {}, {} ms, jetons {} envoyés + {} générés = {}",
                    model, latencyMs, promptTokens, completionTokens, promptTokens + completionTokens);
            return new Completion(text, latencyMs, promptTokens, completionTokens);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badGateway("Réponse illisible du service IA. Réessayez.");
        }
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) return true;
        }
        return false;
    }

    private ApiException mapHttpError(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String body = e.getResponseBodyAsString();
        // On ne journalise jamais le contenu du CV, seulement la réponse d'erreur du fournisseur
        log.warn("Appel LLM : HTTP {} - {}", status, body);

        if (status == 401 || status == 403) {
            return ApiException.badGateway("Clé API du LLM invalide ou non autorisée.");
        }
        if (status == 413) {
            return ApiException.badRequest("CV et offre trop longs pour le modèle. Raccourcissez la description de l'offre.");
        }
        if (status == 429) {
            return ApiException.tooManyRequests("Quota du service IA atteint. Réessayez plus tard.");
        }
        if (status == 503 || status == 529) {
            return ApiException.unavailable("Service IA temporairement surchargé. Réessayez dans un instant.");
        }
        return ApiException.badGateway("Erreur du service IA (code " + status + ").");
    }
}