package com.careerai.compatibility_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppConfig.AiProperties.class)
public class AppConfig {

    @ConfigurationProperties(prefix = "app.ai")
    public record AiProperties(String model, String url, int timeoutSeconds, int numCtx) { }
}