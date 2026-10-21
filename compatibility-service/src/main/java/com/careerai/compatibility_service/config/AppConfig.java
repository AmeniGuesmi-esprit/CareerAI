package com.careerai.compatibility_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppConfig.AiProperties.class)
public class AppConfig {

    /**
     * Paramètres du modèle. temperature et promptVersion sont réglables sans recompiler : leurs valeurs par
     * défaut sont celles retenues par l'évaluation (ModelEvaluationTest, voir le README).
     */
    @ConfigurationProperties(prefix = "app.ai")
    public record AiProperties(String model, String url, int timeoutSeconds, int numCtx,
                               @DefaultValue("0.2") double temperature,
                               @DefaultValue("v1") String promptVersion) {

        public AiProperties {
            if (temperature < 0 || temperature > 2) {
                throw new IllegalArgumentException("app.ai.temperature doit être entre 0 et 2 : " + temperature);
            }
        }
    }
}
