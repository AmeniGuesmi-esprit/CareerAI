package com.careerai.lettre.ai;

import com.careerai.lettre.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Erreurs et cas nominal de l'API du LLM, sans internet : le fournisseur est remplacé par un faux serveur HTTP local
 * qui rejoue des réponses scénarisées (statut, corps, délai, en-tête Retry-After).
 */
@Timeout(30)
class LlmClientTest {

    /** Une réponse du faux serveur. */
    private record Resp(int status, String body, String contentType, String retryAfter, long delayMs) {
        static Resp json(int status, String body) { return new Resp(status, body, "application/json", null, 0); }
        static Resp ok(String content, String finishReason) {
            return json(200, "{\"choices\":[{\"message\":{\"content\":\"" + content + "\"},\"finish_reason\":\"" + finishReason + "\"}]}");
        }
    }

    private HttpServer server;
    private String url;
    private final List<Resp> script = new CopyOnWriteArrayList<>();
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();

    @BeforeEach
    void startFakeProvider() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/v1/chat/completions", this::handle);
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions";
    }

    @AfterEach
    void stopFakeProvider() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        int i = hits.getAndIncrement();
        lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
        Resp r = script.get(Math.min(i, script.size() - 1));
        try {
            if (r.delayMs() > 0) Thread.sleep(r.delayMs());
            byte[] out = r.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", r.contentType());
            if (r.retryAfter() != null) ex.getResponseHeaders().add("Retry-After", r.retryAfter());
            ex.sendResponseHeaders(r.status(), out.length);
            ex.getResponseBody().write(out);
        } catch (IOException | InterruptedException ignored) {
            // le client a pu fermer la connexion (timeout) : sans importance
        } finally {
            ex.close();
        }
    }

    private LlmClient client(String providerUrl, String apiKey, int timeoutSeconds) {
        LlmProperties props = new LlmProperties();
        props.setDefaultProvider("fake");
        props.setTemperature(0.7);
        props.setMaxTokens(500);
        props.setTimeoutSeconds(timeoutSeconds);
        LlmProperties.Provider p = new LlmProperties.Provider();
        p.setUrl(providerUrl);
        p.setApiKey(apiKey);
        p.setModel("fake-model");
        p.setReasoningEffort("low");
        props.getProviders().put("fake", p);
        return new LlmClient(props);
    }

    private LlmClient client() {
        return client(url, "test-key", 5);
    }

    private void expect(Runnable call, HttpStatus status, String messagePart) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getMessage()).contains(messagePart);
        });
    }

    // ───────────────────────── Cas nominal ─────────────────────────

    @Test
    void returnsTheGeneratedTextWithProviderAndModel() {
        script.add(Resp.ok("Bonjour", "stop"));

        LlmClient.Result r = client().chat(null, "système", "utilisateur");

        assertThat(r.text()).isEqualTo("Bonjour");
        assertThat(r.provider()).isEqualTo("fake");
        assertThat(r.model()).isEqualTo("fake-model");
    }

    @Test
    void sendsTheConfiguredParametersAndTheApiKey() throws Exception {
        script.add(Resp.ok("ok", "stop"));

        client().chat("fake", "SYS", "USR");

        assertThat(lastAuth.get()).isEqualTo("Bearer test-key");
        JsonNode body = new ObjectMapper().readTree(lastBody.get());
        assertThat(body.path("model").asText()).isEqualTo("fake-model");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(500);
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.7);
        assertThat(body.path("reasoning_effort").asText()).isEqualTo("low");
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("messages").get(0).path("content").asText()).isEqualTo("SYS");
        assertThat(body.path("messages").get(1).path("role").asText()).isEqualTo("user");
        assertThat(body.path("messages").get(1).path("content").asText()).isEqualTo("USR");
    }

    @Test
    void providerNameIsCaseInsensitive() {
        script.add(Resp.ok("ok", "stop"));
        assertThat(client().resolveName("  FAKE ")).isEqualTo("fake");
        assertThat(client().resolveName(null)).isEqualTo("fake");
    }

    // ───────────────────────── Configuration ─────────────────────────

    @Test
    void unknownProviderIsRejected() {
        script.add(Resp.ok("ok", "stop"));
        expect(() -> client().chat("inconnu", "s", "u"), HttpStatus.BAD_REQUEST, "Fournisseur IA inconnu");
        assertThat(hits.get()).isZero();
    }

    @Test
    void missingApiKeyGives503WithoutCallingTheProvider() {
        script.add(Resp.ok("ok", "stop"));
        expect(() -> client(url, "", 5).chat("fake", "s", "u"), HttpStatus.SERVICE_UNAVAILABLE, "clé API manquante");
        assertThat(hits.get()).isZero();
    }

    // ───────────────────────── Erreurs du fournisseur ─────────────────────────

    @Test
    void invalidKey401Gives502() {
        script.add(Resp.json(401, "{\"error\":{\"message\":\"bad key\"}}"));
        expect(() -> client().chat("fake", "s", "u"), HttpStatus.BAD_GATEWAY, "Clé API refusée");
    }

    @Test
    void forbiddenKey403Gives502() {
        script.add(Resp.json(403, "{\"message\":\"forbidden\"}"));
        expect(() -> client().chat("fake", "s", "u"), HttpStatus.BAD_GATEWAY, "Clé API refusée");
    }

    @Test
    void quotaExceededGives429AfterTwoRetries() {
        script.add(new Resp(429, "{\"error\":{\"message\":\"quota\"}}", "application/json", "0", 0));

        assertThatThrownBy(() -> client().chat("fake", "s", "u")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(e.getMessage()).contains("saturé");
            assertThat(e.getMessage()).contains("Détail : quota");
        });
        assertThat(hits.get()).isEqualTo(3); // 1 essai + 2 nouvelles tentatives
    }

    @Test
    void a429ThenASuccessIsRetriedTransparently() {
        script.add(new Resp(429, "{\"error\":{\"message\":\"quota\"}}", "application/json", "0", 0));
        script.add(Resp.ok("Réussi", "stop"));

        LlmClient.Result r = client().chat("fake", "s", "u");

        assertThat(r.text()).isEqualTo("Réussi");
        assertThat(hits.get()).isEqualTo(2);
    }

    @Test
    void serverError500Gives502WithTheProviderDetail() {
        script.add(Resp.json(500, "{\"error\":{\"message\":\"boom\"}}"));
        expect(() -> client().chat("fake", "s", "u"), HttpStatus.BAD_GATEWAY, "HTTP 500");
        assertThatThrownBy(() -> client().chat("fake", "s", "u"))
                .hasMessageContaining("Détail : boom");
    }

    // ───────────────────────── Réponses inexploitables ─────────────────────────

    @Test
    void emptyAnswerGives502() {
        script.add(Resp.ok("", "stop"));
        expect(() -> client().chat("fake", "s", "u"), HttpStatus.BAD_GATEWAY, "réponse vide");
    }

    @Test
    void truncatedAnswerGives502() {
        script.add(Resp.ok("Début de la lett", "length"));
        expect(() -> client().chat("fake", "s", "u"), HttpStatus.BAD_GATEWAY, "coupée");
    }

    @Test
    void unreadableAnswerGives502() {
        script.add(Resp.json(200, "<html>pas du json</html>"));
        expect(() -> client().chat("fake", "s", "u"), HttpStatus.BAD_GATEWAY, "illisible");
    }

    // ───────────────────────── Réseau ─────────────────────────

    @Test
    void slowProviderGives504NotA500() {
        script.add(new Resp(200, "{}", "application/json", null, 3500));
        expect(() -> client(url, "test-key", 1).chat("fake", "s", "u"), HttpStatus.GATEWAY_TIMEOUT, "trop de temps");
    }

    @Test
    void unreachableProviderGives502() throws IOException {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        String deadUrl = "http://127.0.0.1:" + closedPort + "/v1/chat/completions";
        expect(() -> client(deadUrl, "test-key", 5).chat("fake", "s", "u"), HttpStatus.BAD_GATEWAY, "Impossible de joindre");
    }
}
