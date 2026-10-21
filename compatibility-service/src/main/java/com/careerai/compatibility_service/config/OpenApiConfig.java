package com.careerai.compatibility_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger : bouton « Authorize » pour coller le JWT obtenu à la connexion (POST /api/users/auth/login). */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI compatibilityOpenApi() {
        return new OpenAPI()
                .info(new Info().title("CareerAI — compatibility-service")
                        .description("Score de compatibilité CV / offre par un agent IA local (Ollama).")
                        .version("1.0"))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
