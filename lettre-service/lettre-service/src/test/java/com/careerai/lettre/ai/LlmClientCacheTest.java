package com.careerai.lettre.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cache des réponses IA et métriques de latence, mesurés contre un faux fournisseur local
 * qui répond en {@value #PROVIDER_DELAY_MS} ms : aucun appel réseau, aucun quota consommé.
 */
@Timeout(30)
class LlmClientCacheTest {

    private static final long PROVIDER_DELAY_MS = 200;

    private HttpServer server;
    private String url;
    private final AtomicInteger hits = new AtomicInteger();

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
        int n = hits.incrementAndGet();
        ex.getRequestBody().readAllBytes();
        try {
            Thread.sleep(PROVIDER_DELAY_MS);
            byte[] out = ("{\"choices\":[{\"message\":{\"content\":\"Réponse " + n + "\"},\"finish_reason\":\"stop\"}]}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            ex.close();
        }
    }

    private LlmClient client(long ttlSeconds, int maxEntries) {
        LlmProperties props = new LlmProperties();
        props.setDefaultProvider("fake");
        props.setTimeoutSeconds(5);
        props.setCacheTtlSeconds(ttlSeconds);
        props.setCacheMaxEntries(maxEntries);
        LlmProperties.Provider p = new LlmProperties.Provider();
        p.setUrl(url);
        p.setApiKey("test-key");
        p.setModel("fake-model");
        props.getProviders().put("fake", p);
        return new LlmClient(props);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> provider(Map<String, Object> metrics, String name) {
        return (Map<String, Object>) ((Map<String, Object>) metrics.get("providers")).get(name);
    }

    // ───────────────────────── Cache ─────────────────────────

    @Test
    void anIdenticalRequestIsServedFromTheCacheMuchFaster() {
        LlmClient llm = client(300, 100);

        long t0 = System.nanoTime();
        LlmClient.Result first = llm.chat("fake", "sys", "user");
        long missMs = (System.nanoTime() - t0) / 1_000_000;
        long t1 = System.nanoTime();
        LlmClient.Result second = llm.chat("fake", "sys", "user");
        long hitMs = (System.nanoTime() - t1) / 1_000_000;

        assertThat(second).isEqualTo(first);
        assertThat(hits.get()).isEqualTo(1);                 // le fournisseur n'est appelé qu'une fois
        assertThat(missMs).isGreaterThanOrEqualTo(PROVIDER_DELAY_MS);
        assertThat(hitMs).isLessThan(PROVIDER_DELAY_MS / 4); // gain de latence > 75 %

        Map<String, Object> m = llm.metrics();
        assertThat(m.get("cacheHits")).isEqualTo(1L);
        assertThat(m.get("cacheMisses")).isEqualTo(1L);
        assertThat(m.get("cacheHitRatePercent")).isEqualTo(50.0);
        assertThat((double) m.get("cacheHitAverageLatencyMs")).isLessThan(PROVIDER_DELAY_MS / 4.0);
        assertThat(m.get("cacheSize")).isEqualTo(1);
    }

    @Test
    void changingOneCharacterOfThePromptOrTheProviderIsAMiss() {
        LlmClient llm = client(300, 100);

        llm.chat("fake", "sys", "user");
        llm.chat("fake", "sys", "user!");
        llm.chat("fake", "sys!", "user");

        assertThat(hits.get()).isEqualTo(3);
        assertThat(llm.metrics().get("cacheHits")).isEqualTo(0L);
    }

    @Test
    void anExpiredEntryIsFetchedAgain() throws InterruptedException {
        LlmClient llm = client(1, 100);

        llm.chat("fake", "s", "u");
        Thread.sleep(1100);
        LlmClient.Result again = llm.chat("fake", "s", "u");

        assertThat(hits.get()).isEqualTo(2);
        assertThat(again.text()).isEqualTo("Réponse 2");
    }

    @Test
    void theLeastRecentlyUsedEntryIsEvictedWhenTheCacheIsFull() {
        LlmClient llm = client(300, 2);

        llm.chat("fake", "s", "A");
        llm.chat("fake", "s", "B");
        llm.chat("fake", "s", "A");          // hit : A devient le plus récent
        llm.chat("fake", "s", "C");          // évince B
        assertThat(llm.cacheSize()).isEqualTo(2);

        llm.chat("fake", "s", "A");          // toujours en cache
        llm.chat("fake", "s", "B");          // à nouveau demandé au fournisseur

        assertThat(hits.get()).isEqualTo(4); // A, B, C, B
    }

    @Test
    void benchmarksBypassTheCache() {
        LlmClient llm = client(300, 100);

        llm.chat("fake", "s", "u", false);
        llm.chat("fake", "s", "u", false);

        assertThat(hits.get()).isEqualTo(2);
        assertThat(llm.metrics().get("cacheMisses")).isEqualTo(0L);
        assertThat(llm.cacheSize()).isZero();
    }

    @Test
    void aZeroTtlDisablesTheCache() {
        LlmClient llm = client(0, 100);

        llm.chat("fake", "s", "u");
        llm.chat("fake", "s", "u");

        assertThat(hits.get()).isEqualTo(2);
        assertThat(llm.cacheSize()).isZero();
    }

    // ───────────────────────── Latence ─────────────────────────

    @Test
    void latencyPercentilesAndSuccessRateArePublishedPerProvider() {
        LlmClient llm = client(300, 100);
        for (int i = 0; i < 5; i++) llm.chat("fake", "s", "u" + i);

        Map<String, Object> fake = provider(llm.metrics(), "fake");

        assertThat(fake.get("calls")).isEqualTo(5L);
        assertThat(fake.get("failures")).isEqualTo(0L);
        assertThat(fake.get("successRatePercent")).isEqualTo(100.0);
        assertThat((long) fake.get("p50LatencyMs")).isGreaterThanOrEqualTo(PROVIDER_DELAY_MS);
        assertThat((long) fake.get("p95LatencyMs")).isGreaterThanOrEqualTo((long) fake.get("p50LatencyMs"));
        assertThat((long) fake.get("maxLatencyMs")).isGreaterThanOrEqualTo((long) fake.get("p95LatencyMs"));
        assertThat((long) fake.get("minLatencyMs")).isLessThanOrEqualTo((long) fake.get("p50LatencyMs"));
    }

    @Test
    void resetClearsCountersAndCache() {
        LlmClient llm = client(300, 100);
        llm.chat("fake", "s", "u");
        llm.chat("fake", "s", "u");

        llm.resetMetrics();

        Map<String, Object> m = llm.metrics();
        assertThat(m.get("successfulCalls")).isEqualTo(0L);
        assertThat(m.get("cacheHits")).isEqualTo(0L);
        assertThat(m.get("cacheSize")).isEqualTo(0);
        assertThat((Map<?, ?>) m.get("providers")).isEmpty();
    }

    @Test
    void percentileUsesTheNearestRankMethod() {
        List<Long> v = List.of(100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L, 900L, 1000L);
        assertThat(LlmClient.percentile(v, 50)).isEqualTo(500L);
        assertThat(LlmClient.percentile(v, 95)).isEqualTo(1000L);
        assertThat(LlmClient.percentile(v, 0)).isEqualTo(100L);
        assertThat(LlmClient.percentile(List.of(), 95)).isZero();
    }
}
