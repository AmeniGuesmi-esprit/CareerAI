package com.careerai.lettre.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Configuration des fournisseurs LLM (préfixe app.llm dans application.properties). */
@Data
@Component
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

    private String defaultProvider = "mistral";
    private double temperature = 0.7;
    private int maxTokens = 1500;
    private int timeoutSeconds = 60;
    /** Durée de vie d'une réponse en cache (secondes). 0 = cache désactivé. */
    private long cacheTtlSeconds = 300;
    /** Nombre maximal de réponses gardées en cache (LRU). */
    private int cacheMaxEntries = 100;
    private Map<String, Provider> providers = new LinkedHashMap<>();

    @Data
    public static class Provider {
        /** URL complète de /chat/completions (API compatible OpenAI). */
        private String url;
        private String apiKey;
        private String model;
        /** Optionnel. Pour les modèles « raisonneurs » (ex. Cohere) : « none » coupe la réflexion. Vide = non envoyé. */
        private String reasoningEffort;

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}