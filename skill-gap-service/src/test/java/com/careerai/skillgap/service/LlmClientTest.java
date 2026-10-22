package com.careerai.skillgap.service;

import com.careerai.skillgap.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exigence du sujet : gestion des erreurs d'appel API (timeout, quota, clé absente).
 * Un faux serveur Groq local renvoie chaque cas d'erreur ; aucun appel réseau réel.
 */
class LlmClientTest {

    private HttpServer server;
    private volatile int status;
    private volatile String responseBody;
    private volatile long delayMs;
    private volatile String lastAuthorization;
    private volatile String lastRequestBody;

    @BeforeEach
    void startFakeGroq() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/v1/chat/completions", exchange -> {
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                if (delayMs > 0) Thread.sleep(delayMs);
                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException | IOException ignored) {
                // client parti (timeout)
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopFakeGroq() {
        server.stop(0);
    }

    private LlmClient client(String apiKey) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        return new LlmClient(new ObjectMapper(), url, apiKey, "test-model", 100, "low", 1);
    }

    private void respond(int status, String body) {
        this.status = status;
        this.responseBody = body;
    }

    private static String completion(String content, String finishReason) {
        return """
                {"choices":[{"message":{"role":"assistant","content":%s},"finish_reason":"%s"}]}
                """.formatted(content == null ? "null" : "\"" + content + "\"", finishReason);
    }

    private static void assertApiError(Runnable call, HttpStatus expected, String messagePart) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(messagePart)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(expected));
    }

    // ── Appel normal ──

    @Test
    void returnsTheModelAnswer() {
        respond(200, completion("{\\\"matchScore\\\": 80}", "stop"));

        assertThat(client("gsk_test").complete("system", "user")).isEqualTo("{\"matchScore\": 80}");
    }

    @Test
    void sendsModelSettingsAndTheApiKey() {
        respond(200, completion("ok", "stop"));

        client("gsk_test").complete("Tu es un conseiller.", "<offre>...</offre>");

        assertThat(lastAuthorization).isEqualTo("Bearer gsk_test");
        assertThat(lastRequestBody).contains("\"model\":\"test-model\"", "\"max_tokens\":100",
                "\"reasoning_effort\":\"low\"", "\"role\":\"system\"", "Tu es un conseiller.");
    }

    /** Mesures pour l'évaluation des performances : latence de l'appel et jetons consommés. */
    @Test
    void measuresLatencyAndTokenUsage() {
        delayMs = 150;
        respond(200, """
                {"choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":1200,"completion_tokens":350,"total_tokens":1550}}
                """);

        LlmClient.Completion c = client("k").call("s", "u");

        assertThat(c.text()).isEqualTo("ok");
        assertThat(c.latencyMs()).isGreaterThanOrEqualTo(150);
        assertThat(c.promptTokens()).isEqualTo(1200);
        assertThat(c.completionTokens()).isEqualTo(350);
        assertThat(c.totalTokens()).isEqualTo(1550);
    }

    // ── Clé absente ──

    @Test
    void missingKeyFailsBeforeAnyNetworkCall() {
        LlmClient groq = new LlmClient(new ObjectMapper(), "https://api.groq.com/openai/v1", "", "m", 100, "", 1);

        assertApiError(() -> groq.complete("s", "u"), HttpStatus.SERVICE_UNAVAILABLE, "LLM_API_KEY");
    }

    /** Un LLM local (Ollama...) n'exige pas de clé. */
    @Test
    void localLlmWorksWithoutKey() {
        respond(200, completion("ok", "stop"));

        assertThat(client("").complete("s", "u")).isEqualTo("ok");
        assertThat(lastAuthorization).isNull();
    }

    // ── Erreurs du fournisseur ──

    @Test
    void invalidKey() {
        respond(401, "{\"error\":{\"message\":\"Invalid API Key\"}}");
        assertApiError(() -> client("gsk_faux").complete("s", "u"), HttpStatus.BAD_GATEWAY, "Clé API du LLM invalide");
    }

    @Test
    void quotaExceeded() {
        respond(429, "{\"error\":{\"message\":\"Rate limit reached\"}}");
        assertApiError(() -> client("k").complete("s", "u"), HttpStatus.TOO_MANY_REQUESTS, "Quota");
    }

    @Test
    void requestTooLarge() {
        respond(413, "{\"error\":{\"message\":\"Request too large\"}}");
        assertApiError(() -> client("k").complete("s", "u"), HttpStatus.BAD_REQUEST, "trop longs");
    }

    @Test
    void providerOverloaded() {
        respond(503, "{\"error\":{\"message\":\"Service unavailable\"}}");
        assertApiError(() -> client("k").complete("s", "u"), HttpStatus.SERVICE_UNAVAILABLE, "surchargé");
    }

    @Test
    void otherProviderErrorShowsItsCode() {
        respond(500, "{}");
        assertApiError(() -> client("k").complete("s", "u"), HttpStatus.BAD_GATEWAY, "code 500");
    }

    @Test
    void timeout() {
        delayMs = 2_500;   // délai de lecture du client : 1 s
        respond(200, completion("trop tard", "stop"));
        assertApiError(() -> client("k").complete("s", "u"), HttpStatus.GATEWAY_TIMEOUT, "timeout");
    }

    @Test
    void unreachableServer() {
        LlmClient client = client("k");
        server.stop(0);   // plus personne n'écoute sur le port
        assertApiError(() -> client.complete("s", "u"), HttpStatus.SERVICE_UNAVAILABLE, "injoignable");
    }

    // ── Réponses inexploitables ──

    @Test
    void truncatedAnswer() {
        respond(200, completion("{\\\"matchScore\\\": 8", "length"));
        assertApiError(() -> client("k").complete("s", "u"), HttpStatus.BAD_GATEWAY, "tronquée");
    }

    @Test
    void emptyAnswer() {
        respond(200, completion(null, "stop"));
        assertApiError(() -> client("k").complete("s", "u"), HttpStatus.BAD_GATEWAY, "vide");
    }

    @Test
    void unreadableAnswer() {
        respond(200, "<html>Bad gateway</html>");
        assertApiError(() -> client("k").complete("s", "u"), HttpStatus.BAD_GATEWAY, "illisible");
    }
}
