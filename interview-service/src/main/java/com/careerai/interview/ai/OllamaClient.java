package com.careerai.interview.ai;

import com.careerai.interview.config.InterviewConfig.OllamaProperties;
import com.careerai.interview.config.InterviewConfig.RagProperties;
import com.careerai.interview.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class OllamaClient {

    public record ModelInfo(String name, String digest) { }

    private final OllamaProperties props;
    private final int embedBatchSize;
    private final RestClient restClient;

    public OllamaClient(OllamaProperties props, RagProperties rag) {
        this.props = props;
        this.embedBatchSize = rag.embedBatchSize();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeoutSeconds() * 1_000);
        factory.setReadTimeout(props.readTimeoutSeconds() * 1_000);
        this.restClient = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    public String chat(String system, String user, Map<String, Object> jsonSchema, int maxTokens) {
        Map<String, Object> body = Map.of(
                "model", props.chatModel(),
                "stream", false,
                "think", props.think(),
                "keep_alive", props.keepAlive(),
                "format", jsonSchema,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)),
                "options", Map.of(
                        "num_ctx", props.numCtx(),
                        "temperature", props.temperature(),
                        "seed", props.seed(),
                        "num_predict", maxTokens));
        JsonNode response = call(() -> restClient.post().uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class));
        String content = response == null ? "" : response.path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE", "Réponse vide du modèle de génération.");
        }
        return content;
    }

    public List<float[]> embed(List<String> inputs) {
        List<float[]> out = new ArrayList<>(inputs.size());
        for (int from = 0; from < inputs.size(); from += embedBatchSize) {
            List<String> batch = inputs.subList(from, Math.min(inputs.size(), from + embedBatchSize));
            Map<String, Object> body = Map.of("model", props.embedModel(), "input", batch);
            JsonNode response = call(() -> restClient.post().uri("/api/embed")
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class));
            JsonNode vectors = response == null ? null : response.path("embeddings");
            if (vectors == null || vectors.size() != batch.size()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE", "Embeddings incomplets renvoyés par Ollama.");
            }
            for (JsonNode v : vectors) {
                out.add(normalize(v));
            }
        }
        return out;
    }

    public List<ModelInfo> tags() {
        JsonNode response = call(() -> restClient.get().uri("/api/tags").retrieve().body(JsonNode.class));
        List<ModelInfo> models = new ArrayList<>();
        if (response != null) {
            for (JsonNode m : response.path("models")) {
                models.add(new ModelInfo(m.path("name").asText(), m.path("digest").asText(null)));
            }
        }
        return models;
    }

    public boolean hasModel(String name) {
        return tags().stream().anyMatch(m -> m.name().equals(name) || m.name().equals(name + ":latest"));
    }

    public String chatModel() {
        return props.chatModel();
    }

    public String embedModel() {
        return props.embedModel();
    }

    public int seed() {
        return props.seed();
    }

    private static float[] normalize(JsonNode vector) {
        float[] v = new float[vector.size()];
        double norm = 0;
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) vector.get(i).asDouble();
            norm += v[i] * v[i];
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) {
                v[i] = (float) (v[i] / norm);
            }
        }
        return v;
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            String detail = e.getResponseBodyAsString();
            if (e.getStatusCode().value() == 404 && detail.contains("not found")) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_MODEL_MISSING",
                        "Modèle absent d'Ollama. Lancez : ollama pull " + props.chatModel() + " / " + props.embedModel());
            }
            log.warn("Erreur Ollama : HTTP {} - {}", e.getStatusCode().value(),
                    detail.length() > 300 ? detail.substring(0, 300) : detail);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_ERROR",
                    "Ollama a renvoyé une erreur (HTTP " + e.getStatusCode().value() + ").");
        } catch (RestClientException e) {
            if (causedBy(e, SocketTimeoutException.class)) {
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "Ollama n'a pas répondu à temps.");
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE",
                    "Ollama est injoignable (" + props.baseUrl() + "). Vérifiez qu'il est démarré.");
        }
    }

    private static boolean causedBy(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
