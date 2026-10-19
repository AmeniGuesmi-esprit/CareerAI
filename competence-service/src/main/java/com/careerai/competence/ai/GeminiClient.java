package com.careerai.competence.ai;

import com.careerai.competence.exception.AiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GeminiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient rest;
    private final String apiKey;
    private final String model;

    public GeminiClient(@Value("${app.gemini.api-key:}") String apiKey,
                        @Value("${app.gemini.model:gemini-3.1-flash-lite}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        this.rest = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .requestFactory(factory)
                .build();
    }

    /** Réponse en texte libre (conseil). */
    public String generateText(String system, String user) {
        Map<String, Object> config = Map.of("temperature", 0.4, "maxOutputTokens", 2048);
        return call(system, user, config);
    }

    /** Réponse JSON forcée par un schéma (juge). */
    public JsonNode generateJson(String system, String user, Map<String, Object> schema) {
        Map<String, Object> config = Map.of(
                "temperature", 0.0,
                "maxOutputTokens", 1024,
                "responseMimeType", "application/json",
                "responseSchema", schema);
        try {
            return MAPPER.readTree(call(system, user, config));
        } catch (JsonProcessingException e) {
            throw new AiException(HttpStatus.BAD_GATEWAY, "Réponse IA illisible.");
        }
    }

    private String call(String system, String user, Map<String, Object> generationConfig) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Clé Gemini non configurée (variable GEMINI_API_KEY).");
        }
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", user)))),
                "generationConfig", generationConfig);
        try {
            JsonNode res = rest.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            StringBuilder sb = new StringBuilder();
            if (res != null) {
                for (JsonNode part : res.path("candidates").path(0).path("content").path("parts")) {
                    sb.append(part.path("text").asText(""));
                }
            }
            String out = sb.toString().strip();
            if (out.isEmpty()) {
                throw new AiException(HttpStatus.BAD_GATEWAY,
                        "Le modèle n'a pas renvoyé de réponse. Reformule ton prompt.");
            }
            return out;

        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            log.warn("Gemini a répondu {} : {}", code, e.getResponseBodyAsString());
            if (code == 429) throw new AiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Quota Gemini atteint, réessaie dans une minute.");
            if (code == 400 || code == 401 || code == 403) throw new AiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Gemini a refusé la requête (clé invalide ou non autorisée ?).");
            if (code == 404) throw new AiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Modèle Gemini introuvable : vérifie app.gemini.model.");
            throw new AiException(HttpStatus.SERVICE_UNAVAILABLE, "Service IA indisponible (code " + code + ").");
        } catch (RestClientException e) {
            log.warn("Gemini injoignable : {}", e.getMessage());
            throw new AiException(HttpStatus.SERVICE_UNAVAILABLE, "Service IA injoignable, réessaie dans un instant.");
        }
    }
}
