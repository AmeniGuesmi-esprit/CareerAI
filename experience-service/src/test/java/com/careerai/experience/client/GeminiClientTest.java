package com.careerai.experience.client;

import com.careerai.experience.exception.GeminiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste GeminiClient contre un faux serveur HTTP local (com.sun.net.httpserver, inclus dans le JDK) :
 * aucun appel reel a Google, aucune dependance supplementaire. Le backoff est regle a 0 ms.
 */
class GeminiClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SCHEMA = "{\"type\":\"OBJECT\",\"properties\":{\"ok\":{\"type\":\"BOOLEAN\"}}}";

    private record Stub(int status, String body, String contentType) {
        Stub(int status, String body) {
            this(status, body, "application/json");
        }
    }

    private record RecordedRequest(String method, String path, String apiKey, String body) {
    }

    private HttpServer server;
    private final ConcurrentLinkedDeque<Stub> stubs = new ConcurrentLinkedDeque<>();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile Stub lastStub;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            requests.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("x-goog-api-key"),
                    new String(requestBody, StandardCharsets.UTF_8)));

            Stub stub = stubs.pollFirst();
            if (stub == null) {
                stub = lastStub; // plus de reponse programmee : on repete la derniere
            } else {
                lastStub = stub;
            }
            byte[] out = stub.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", stub.contentType());
            exchange.sendResponseHeaders(stub.status(), out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // ---------- helpers ----------

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta";
    }

    private GeminiClient client(String apiKey, int maxAttempts) {
        return new GeminiClient(MAPPER, apiKey, baseUrl(), "gemini-test", 2000, 5000, maxAttempts, 0);
    }

    private void respond(int status, String body) {
        stubs.addLast(new Stub(status, body));
    }

    private void respond(int status, String body, String contentType) {
        stubs.addLast(new Stub(status, body, contentType));
    }

    /** Reponse Gemini valide contenant {@code text}. */
    private static String okBody(String text) throws JsonProcessingException {
        ObjectNode root = MAPPER.createObjectNode();
        root.putArray("candidates").addObject()
                .put("finishReason", "STOP")
                .putObject("content").putArray("parts").addObject().put("text", text);
        return MAPPER.writeValueAsString(root);
    }

    // =====================================================================
    // Cas nominal
    // =====================================================================

    @Test
    @DisplayName("generate : retourne le texte du premier candidat")
    void generate_returnsCandidateText() throws Exception {
        respond(200, okBody("{\"ok\":true}"));

        String result = client("test-key", 3).generate("Tu es un assistant", "Bonjour", 0.6, SCHEMA);

        assertThat(result).isEqualTo("{\"ok\":true}");
        assertThat(requests).hasSize(1);
    }

    @Test
    @DisplayName("generate : envoie POST sur /models/{model}:generateContent avec la cle dans l'en-tete")
    void generate_sendsCorrectRequest() throws Exception {
        respond(200, okBody("{}"));

        client("secret-key", 3).generate("Systeme", "Question", 0.6, SCHEMA);

        RecordedRequest request = requests.get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1beta/models/gemini-test:generateContent");
        assertThat(request.apiKey()).isEqualTo("secret-key");
        assertThat(request.path()).doesNotContain("secret-key"); // la cle ne doit jamais etre dans l'URL
    }

    @Test
    @DisplayName("generate : le corps contient prompt systeme, prompt utilisateur, temperature et schema")
    void generate_buildsExpectedBody() throws Exception {
        respond(200, okBody("{}"));

        client("k", 3).generate("Systeme", "Question", 0.6, SCHEMA);

        JsonNode body = MAPPER.readTree(requests.get(0).body());
        assertThat(body.path("systemInstruction").path("parts").path(0).path("text").asText())
                .isEqualTo("Systeme");
        assertThat(body.path("contents").path(0).path("role").asText()).isEqualTo("user");
        assertThat(body.path("contents").path(0).path("parts").path(0).path("text").asText())
                .isEqualTo("Question");
        assertThat(body.path("generationConfig").path("temperature").asDouble()).isEqualTo(0.6);
        assertThat(body.path("generationConfig").path("responseMimeType").asText())
                .isEqualTo("application/json");
        assertThat(body.path("generationConfig").path("responseSchema").path("type").asText())
                .isEqualTo("OBJECT");
    }

    @Test
    @DisplayName("generate : sans prompt systeme ni schema, ces champs sont omis")
    void generate_omitsOptionalFields() throws Exception {
        respond(200, okBody("{}"));

        client("k", 3).generate(null, "Question", 0.1, null);

        JsonNode body = MAPPER.readTree(requests.get(0).body());
        assertThat(body.has("systemInstruction")).isFalse();
        assertThat(body.path("generationConfig").has("responseSchema")).isFalse();
    }

    @Test
    @DisplayName("generate : prompt systeme et schema blancs sont aussi omis")
    void generate_omitsBlankOptionalFields() throws Exception {
        respond(200, okBody("{}"));

        client("k", 3).generate("   ", "Question", 0.1, "  ");

        JsonNode body = MAPPER.readTree(requests.get(0).body());
        assertThat(body.has("systemInstruction")).isFalse();
        assertThat(body.path("generationConfig").has("responseSchema")).isFalse();
    }

    // =====================================================================
    // Configuration
    // =====================================================================

    @Test
    @DisplayName("generate : cle API absente -> GeminiException sans appel reseau")
    void generate_blankApiKey_throws() {
        assertThatThrownBy(() -> client("", 3).generate("s", "u", 0.1, null))
                .isInstanceOf(GeminiException.class)
                .hasMessageContaining("GEMINI_API_KEY");
        assertThatThrownBy(() -> client("   ", 3).generate("s", "u", 0.1, null))
                .isInstanceOf(GeminiException.class);
        assertThatThrownBy(() -> client(null, 3).generate("s", "u", 0.1, null))
                .isInstanceOf(GeminiException.class);

        assertThat(requests).isEmpty();
    }

    @Test
    @DisplayName("generate : schema JSON invalide -> GeminiException sans appel reseau")
    void generate_invalidSchema_throws() {
        assertThatThrownBy(() -> client("k", 3).generate("s", "u", 0.1, "{pas du json"))
                .isInstanceOf(GeminiException.class)
                .hasMessageContaining("Schema JSON invalide");

        assertThat(requests).isEmpty();
    }

    // =====================================================================
    // Erreurs HTTP et retry
    // =====================================================================

    @Test
    @DisplayName("generate : erreur 400 (non retentable) -> GeminiException immediate, 1 seule requete")
    void generate_clientError_noRetry() {
        respond(400, "{\"error\":\"requete invalide\"}");

        assertThatThrownBy(() -> client("k", 3).generate("s", "u", 0.1, null))
                .isInstanceOf(GeminiException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("requete invalide");

        assertThat(requests).hasSize(1);
    }

    @Test
    @DisplayName("generate : corps d'erreur tres long tronque a 500 caracteres")
    void generate_longErrorBodyIsTruncated() {
        respond(400, "x".repeat(2000));

        assertThatThrownBy(() -> client("k", 3).generate("s", "u", 0.1, null))
                .isInstanceOf(GeminiException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).endsWith("...");
                    assertThat(e.getMessage().length()).isLessThan(600);
                });
    }

    @Test
    @DisplayName("generate : 503 puis 200 -> retry automatique et succes")
    void generate_serverErrorThenSuccess_retries() throws Exception {
        respond(503, "{\"error\":\"indisponible\"}");
        respond(200, okBody("{\"ok\":true}"));

        String result = client("k", 3).generate("s", "u", 0.1, null);

        assertThat(result).isEqualTo("{\"ok\":true}");
        assertThat(requests).hasSize(2);
    }

    @Test
    @DisplayName("generate : 429 (quota) est retente puis echoue apres 3 tentatives")
    void generate_rateLimited_retriesThenFails() {
        respond(429, "{\"error\":\"quota\"}");

        assertThatThrownBy(() -> client("k", 3).generate("s", "u", 0.1, null))
                .isInstanceOf(GeminiException.class)
                .hasMessageContaining("apres 3 tentatives")
                .hasMessageContaining("429");

        assertThat(requests).hasSize(3);
    }

    @Test
    @DisplayName("generate : maxAttempts <= 0 est ramene a 1 tentative")
    void generate_nonPositiveMaxAttemptsMeansOneAttempt() {
        respond(500, "{\"error\":\"panne\"}");

        assertThatThrownBy(() -> client("k", 0).generate("s", "u", 0.1, null))
                .isInstanceOf(GeminiException.class)
                .hasMessageContaining("apres 1 tentatives");

        assertThat(requests).hasSize(1);
    }

    @Test
    @DisplayName("generate : serveur injoignable -> GeminiException apres 3 tentatives (cause reseau)")
    void generate_connectionRefused_throwsAfterRetries() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        } // le port est maintenant ferme

        GeminiClient unreachable = new GeminiClient(MAPPER, "k",
                "http://127.0.0.1:" + closedPort + "/v1beta", "gemini-test", 500, 500, 3, 0);

        assertThatThrownBy(() -> unreachable.generate("s", "u", 0.1, null))
                .isInstanceOf(GeminiException.class)
                .hasMessageContaining("injoignable apres 3 tentatives")
                .hasCauseInstanceOf(ResourceAccessException.class);
    }

    // =====================================================================
    // Reponses 200 au format inattendu (regression : RestClientException ... application/octet-stream)
    // =====================================================================

    @Test
    @DisplayName("generate : JSON valide servi en application/octet-stream -> texte retourne")
    void generate_octetStreamContentType_isParsed() throws Exception {
        respond(200, okBody("{\"ok\":true}"), "application/octet-stream");

        assertThat(client("k", 1).generate("s", "u", 0.1, null)).isEqualTo("{\"ok\":true}");
    }

    @Test
    @DisplayName("generate : corps 200 non JSON puis succes -> retry transparent")
    void generate_nonJsonBodyThenSuccess_retries() throws Exception {
        respond(200, "<html>Bad gateway</html>", "text/html");
        respond(200, okBody("{\"ok\":true}"));

        assertThat(client("k", 3).generate("s", "u", 0.1, null)).isEqualTo("{\"ok\":true}");
        assertThat(requests).hasSize(2);
    }

    @Test
    @DisplayName("generate : corps 200 non JSON en permanence -> GeminiException avec extrait du corps")
    void generate_nonJsonBodyAlways_throws() {
        respond(200, "pas du json", "application/octet-stream");

        assertThatThrownBy(() -> client("k", 2).generate("s", "u", 0.1, null))
                .isInstanceOf(GeminiException.class)
                .hasMessageContaining("inexploitable apres 2 tentatives")
                .hasMessageContaining("pas du json");
        assertThat(requests).hasSize(2);
    }

    @Test
    @DisplayName("generate : corps 200 vide -> GeminiException apres retries")
    void generate_emptyBody_throws() {
        respond(200, "");

        assertThatThrownBy(() -> client("k", 2).generate("s", "u", 0.1, null))
                .isInstanceOf(GeminiException.class)
                .hasMessageContaining("corps de reponse vide");
    }

    @Test
    @DisplayName("generate : timeout de lecture pendant le corps -> retry puis GeminiException (pas de RestClientException brute)")
    void generate_readTimeoutDuringBody_retriesThenGeminiException() throws IOException {
        AtomicInteger hits = new AtomicInteger();
        HttpServer slow = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        slow.createContext("/", exchange -> {
            hits.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0); // chunked : les en-tetes partent, le corps jamais
            try {
                exchange.getResponseBody().flush();
                Thread.sleep(1500);
            } catch (Exception ignored) {
                // le client a deja coupe
            } finally {
                exchange.close();
            }
        });
        // plusieurs threads : sinon la 2e requete attend la fin de la 1re (1,5 s) et n'arrive pas avant le timeout du client
        ExecutorService pool = Executors.newCachedThreadPool();
        slow.setExecutor(pool);
        slow.start();
        try {
            GeminiClient slowClient = new GeminiClient(MAPPER, "k",
                    "http://127.0.0.1:" + slow.getAddress().getPort() + "/v1beta", "gemini-test", 500, 300, 2, 0);

            assertThatThrownBy(() -> slowClient.generate("s", "u", 0.1, null))
                    .isInstanceOf(GeminiException.class)
                    .hasMessageContaining("apres 2 tentatives");
            assertThat(hits.get()).isEqualTo(2);
        } finally {
            slow.stop(0);
            pool.shutdownNow();
        }
    }

    // =====================================================================
    // Reponses 200 inexploitables
    // =====================================================================

    @Test
    @DisplayName("generate : prompt bloque par Gemini -> message avec la raison du blocage")
    void generate_blockedPrompt_throws() {
        respond(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");

        assertThatThrownBy(() -> client("k", 3).generate("s", "u", 0.1, null))
                .isInstanceOf(GeminiException.class)
                .hasMessageContaining("bloque")
                .hasMessageContaining("SAFETY");
    }

    @Test
    @DisplayName("generate : aucun candidat -> GeminiException")
    void generate_noCandidate_throws() {
        respond(200, "{\"candidates\":[]}");

        assertThatThrownBy(() -> client("k", 3).generate("s", "u", 0.1, null))
                .isInstanceOf(GeminiException.class)
                .hasMessageContaining("sans candidat");
    }

    @Test
    @DisplayName("generate : texte vide -> GeminiException avec la finishReason")
    void generate_emptyText_throws() throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.putArray("candidates").addObject()
                .put("finishReason", "MAX_TOKENS")
                .putObject("content").putArray("parts").addObject().put("text", "");
        respond(200, MAPPER.writeValueAsString(root));

        assertThatThrownBy(() -> client("k", 3).generate("s", "u", 0.1, null))
                .isInstanceOf(GeminiException.class)
                .hasMessageContaining("sans contenu")
                .hasMessageContaining("MAX_TOKENS");
    }

    @Test
    @DisplayName("generate : candidat sans parts -> GeminiException (finishReason inconnue)")
    void generate_missingParts_throws() {
        respond(200, "{\"candidates\":[{\"content\":{}}]}");

        assertThatThrownBy(() -> client("k", 3).generate("s", "u", 0.1, null))
                .isInstanceOf(GeminiException.class)
                .hasMessageContaining("sans contenu")
                .hasMessageContaining("inconnu");
    }
}
