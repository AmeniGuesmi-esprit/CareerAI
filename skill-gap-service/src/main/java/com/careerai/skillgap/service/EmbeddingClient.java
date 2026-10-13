package com.careerai.skillgap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Vectorisation (embeddings) via l'API d'inférence Hugging Face (tâche feature-extraction).
 * Chaque texte devient un vecteur normalisé : la similarité cosinus se réduit alors à un produit scalaire.
 */
@Slf4j
@Component
public class EmbeddingClient {

    private static final int BATCH = 16;

    private final ObjectMapper mapper;
    private final RestClient http;
    private final String url;
    private final String apiKey;

    public EmbeddingClient(ObjectMapper mapper,
                           @Value("${rag.embedding-base-url}") String baseUrl,
                           @Value("${rag.embedding-model}") String model,
                           @Value("${rag.hf-api-key:}") String apiKey,
                           @Value("${rag.timeout-seconds:30}") int timeoutSeconds) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.url = baseUrl + "/" + model + "/pipeline/feature-extraction";
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(timeoutSeconds * 1000);
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** false si HF_API_KEY est absente : le RAG est alors simplement désactivé. */
    public boolean available() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Un vecteur normalisé par texte, dans le même ordre. Lève une RuntimeException en cas d'échec. */
    public List<float[]> embed(List<String> texts) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += BATCH) {
            List<String> batch = texts.subList(i, Math.min(i + BATCH, texts.size()));
            out.addAll(embedBatch(batch));
        }
        return out;
    }

    private List<float[]> embedBatch(List<String> batch) {
        String raw = http.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(Map.of("inputs", batch, "options", Map.of("wait_for_model", true)))
                .retrieve()
                .body(String.class);
        try {
            JsonNode root = mapper.readTree(raw);
            if (!root.isArray() || root.size() != batch.size()) {
                throw new IllegalStateException("réponse d'embeddings inattendue");
            }
            List<float[]> vectors = new ArrayList<>();
            for (JsonNode item : root) {
                vectors.add(normalize(toVector(item)));
            }
            return vectors;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("réponse d'embeddings illisible", e);
        }
    }

    /** Accepte un vecteur déjà poolé [dim] ou une matrice par token [tokens][dim] (moyenne). */
    private static float[] toVector(JsonNode node) {
        if (node.isEmpty()) throw new IllegalStateException("vecteur vide");
        if (node.get(0).isNumber()) {
            float[] v = new float[node.size()];
            for (int i = 0; i < v.length; i++) v[i] = (float) node.get(i).asDouble();
            return v;
        }
        int dim = node.get(0).size();
        float[] v = new float[dim];
        for (JsonNode token : node) {
            for (int i = 0; i < dim; i++) v[i] += (float) token.get(i).asDouble();
        }
        for (int i = 0; i < dim; i++) v[i] /= node.size();
        return v;
    }

    static float[] normalize(float[] v) {
        double sum = 0;
        for (float x : v) sum += x * x;
        double norm = Math.sqrt(sum);
        if (norm == 0) return v;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / norm);
        return out;
    }
}
