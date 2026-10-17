package com.careerai.interview.ai;

import com.careerai.interview.config.InterviewConfig.OllamaProperties;
import com.careerai.interview.config.InterviewConfig.RagProperties;
import com.careerai.interview.exception.ApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaClientTest {

    private HttpServer server;
    private String baseUrl;
    private int readTimeoutSeconds = 5;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("\"absent\"")) {
                reply(ex, 404, "{\"error\":\"model 'absent' not found\"}");
            } else if (body.contains("\"slow\"")) {
                sleep(3000);
                reply(ex, 200, "{\"message\":{\"content\":\"{}\"}}");
            } else {
                reply(ex, 200, "{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"ok\\\":true}\"}}");
            }
        });
        server.createContext("/api/embed", ex -> reply(ex, 200,
                "{\"embeddings\":[[3.0,4.0],[0.0,2.0]]}"));
        server.createContext("/api/tags", ex -> reply(ex, 200,
                "{\"models\":[{\"name\":\"qwen3.5:4b\",\"digest\":\"abc\"}]}"));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void chatRenvoieLeContenuJson() {
        assertThat(client().chat("sys", "user", Map.of("type", "object"), 100)).isEqualTo("{\"ok\":true}");
    }

    @Test
    void embedNormaliseLesVecteurs() {
        List<float[]> vectors = client().embed(List.of("a", "b"));
        assertThat(vectors.get(0)[0]).isCloseTo(0.6f, org.assertj.core.data.Offset.offset(1e-6f));
        assertThat(vectors.get(0)[1]).isCloseTo(0.8f, org.assertj.core.data.Offset.offset(1e-6f));
        assertThat(vectors.get(1)[1]).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(1e-6f));
    }

    @Test
    void modeleAbsentDonneUneErreurExplicite() {
        assertThatThrownBy(() -> client().chat("sys", "absent", Map.of(), 10))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("AI_MODEL_MISSING");
    }

    @Test
    void depassementDeDelaiDonneAiTimeout() {
        readTimeoutSeconds = 1;
        assertThatThrownBy(() -> client().chat("sys", "slow", Map.of(), 10))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("AI_TIMEOUT");
    }

    @Test
    void ollamaInjoignableDonneAiUnavailable() {
        server.stop(0);
        assertThatThrownBy(() -> client().chat("sys", "user", Map.of(), 10))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("AI_UNAVAILABLE");
    }

    @Test
    void listeLesModelesInstalles() {
        assertThat(client().hasModel("qwen3.5:4b")).isTrue();
        assertThat(client().hasModel("bge-m3")).isFalse();
    }

    private OllamaClient client() {
        return new OllamaClient(new OllamaProperties(baseUrl, "qwen3.5:4b", "bge-m3", false, 8192, 0.3, 42, "10m", 2,
                readTimeoutSeconds), new RagProperties("../knowledge", "rag-test", 6, 4, 60, 0.7, 32));
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
