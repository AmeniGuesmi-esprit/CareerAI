package com.careerai.compatibility_service.service;

import com.careerai.compatibility_service.dto.AnalysisDtos.AiMetrics;
import com.careerai.compatibility_service.dto.AnalysisDtos.AiResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisCacheTest {

    private static final AiResult RESULT = new AiResult(72, "Bon profil.", List.of("Java"),
            List.of("Kubernetes"), List.of("Ajouter Docker"), List.of("Java"));
    private static final AiMetrics METRICS = new AiMetrics("qwen2.5:7b", 3, 40_000, 2_000, 300, false);

    /** Horloge réglable pour tester l'expiration sans attendre. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-20T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public Instant instant() { return now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    private final MutableClock clock = new MutableClock();

    @Test
    void renvoieLEntreeMiseEnCache() {
        AnalysisCache cache = new AnalysisCache(Duration.ofMinutes(60), 10, clock);
        cache.put("k", RESULT, METRICS);

        assertThat(cache.get("k")).hasValueSatisfying(e -> {
            assertThat(e.result()).isEqualTo(RESULT);
            assertThat(e.metrics()).isEqualTo(METRICS);
        });
        assertThat(cache.hits()).isEqualTo(1);
    }

    @Test
    void compteUnEchecPourUneCleInconnue() {
        AnalysisCache cache = new AnalysisCache(Duration.ofMinutes(60), 10, clock);

        assertThat(cache.get("absente")).isEmpty();
        assertThat(cache.misses()).isEqualTo(1);
    }

    @Test
    void expireUneEntreeApresSaDureeDeVie() {
        AnalysisCache cache = new AnalysisCache(Duration.ofMinutes(60), 10, clock);
        cache.put("k", RESULT, METRICS);

        clock.advance(Duration.ofMinutes(59));
        assertThat(cache.get("k")).isPresent();
        clock.advance(Duration.ofMinutes(1));
        assertThat(cache.get("k")).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    void evinceLEntreeLaMoinsRecemmentUtilisee() {
        AnalysisCache cache = new AnalysisCache(Duration.ofMinutes(60), 2, clock);
        cache.put("a", RESULT, METRICS);
        cache.put("b", RESULT, METRICS);
        cache.get("a"); // "a" devient la plus récente
        cache.put("c", RESULT, METRICS);

        assertThat(cache.get("a")).isPresent();
        assertThat(cache.get("b")).isEmpty();
        assertThat(cache.get("c")).isPresent();
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void neStockeRienQuandLeCacheEstDesactive() {
        AnalysisCache cache = new AnalysisCache(Duration.ZERO, 10, clock);
        cache.put("k", RESULT, METRICS);

        assertThat(cache.enabled()).isFalse();
        assertThat(cache.get("k")).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    void laCleEstUnSha256QuiNeContientPasLeTexteDuCv() {
        String key = AnalysisCache.key("qwen2.5:7b", "prompt", "Développeur Java chez Sofrecom", "offre");

        assertThat(key).hasSize(64).matches("[0-9a-f]+").doesNotContain("Sofrecom");
    }

    @Test
    void laCleDependDeChaqueComposante() {
        String base = AnalysisCache.key("m", "p", "cv", "offre");

        assertThat(AnalysisCache.key("m", "p", "cv", "offre")).isEqualTo(base);
        assertThat(AnalysisCache.key("m2", "p", "cv", "offre")).isNotEqualTo(base);
        assertThat(AnalysisCache.key("m", "p2", "cv", "offre")).isNotEqualTo(base);
        assertThat(AnalysisCache.key("m", "p", "cv2", "offre")).isNotEqualTo(base);
        assertThat(AnalysisCache.key("m", "p", "cv", "offre2")).isNotEqualTo(base);
    }

    @Test
    void leSeparateurEmpecheLesCollisionsParConcatenation() {
        assertThat(AnalysisCache.key("m", "p", "ab", "c")).isNotEqualTo(AnalysisCache.key("m", "p", "a", "bc"));
    }
}
