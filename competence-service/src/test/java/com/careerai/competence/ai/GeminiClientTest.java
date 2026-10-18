package com.careerai.competence.ai;

import com.careerai.competence.exception.AiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pas d'appel réseau : on vérifie uniquement le garde-fou « clé absente ». */
class GeminiClientTest {

    @Test
    void generateText_withoutApiKey_throws503() {
        GeminiClient client = new GeminiClient("", "model");

        assertThatThrownBy(() -> client.generateText("sys", "user"))
                .isInstanceOfSatisfying(AiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).contains("GEMINI_API_KEY");
                });
    }

    @Test
    void generateJson_withBlankApiKey_throws503() {
        GeminiClient client = new GeminiClient("   ", "model");

        assertThatThrownBy(() -> client.generateJson("sys", "user", Map.of()))
                .isInstanceOfSatisfying(AiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void nullApiKey_throws503() {
        GeminiClient client = new GeminiClient(null, "model");

        assertThatThrownBy(() -> client.generateText("sys", "user")).isInstanceOf(AiException.class);
    }
}
