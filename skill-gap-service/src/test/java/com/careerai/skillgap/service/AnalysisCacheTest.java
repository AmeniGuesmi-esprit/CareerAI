package com.careerai.skillgap.service;

import com.careerai.skillgap.dto.AnalysisResult;
import com.careerai.skillgap.dto.AnalysisResult.MissingSkill;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Cache des analyses : économie de jetons et de latence, sans conserver de texte en clair. */
class AnalysisCacheTest {

    private static final AnalysisResult RESULT = new AnalysisResult(70, "Bon profil.", List.of("Java"),
            List.of(new MissingSkill("Docker", "HIGH", "Exigé.", "Conteneuriser une API.")), List.of("Quantifier"));

    /** Horloge réglable pour tester l'expiration sans attendre. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-20T10:00:00Z");

        void advance(Duration d) { now = now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    @Test
    void storedResultIsReturnedForTheSameKey() {
        AnalysisCache cache = new AnalysisCache(Duration.ofMinutes(60), 10, Clock.systemUTC());
        String key = AnalysisCache.key("m", "system", "<offre>Java</offre><cv>Java</cv>");

        assertThat(cache.get(key)).isEmpty();
        cache.put(key, RESULT);

        assertThat(cache.get(key)).contains(RESULT);
    }

    @Test
    void keyDependsOnModelAndPromptsAndHidesTheirContent() {
        String key = AnalysisCache.key("m", "system", "<cv>Développeur Java chez Sofrecom</cv>");

        assertThat(key).hasSize(64).doesNotContain("Java", "Sofrecom");
        assertThat(key).isEqualTo(AnalysisCache.key("m", "system", "<cv>Développeur Java chez Sofrecom</cv>"));
        assertThat(key).isNotEqualTo(AnalysisCache.key("autre-modele", "system", "<cv>Développeur Java chez Sofrecom</cv>"));
        assertThat(key).isNotEqualTo(AnalysisCache.key("m", "system", "<cv>Développeur Python</cv>"));
    }

    @Test
    void entryExpiresAfterItsLifetime() {
        MutableClock clock = new MutableClock();
        AnalysisCache cache = new AnalysisCache(Duration.ofMinutes(60), 10, clock);
        cache.put("k", RESULT);

        clock.advance(Duration.ofMinutes(59));
        assertThat(cache.get("k")).isPresent();

        clock.advance(Duration.ofMinutes(2));
        assertThat(cache.get("k")).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    void leastRecentlyUsedEntryIsEvictedWhenFull() {
        AnalysisCache cache = new AnalysisCache(Duration.ofMinutes(60), 2, Clock.systemUTC());
        cache.put("a", RESULT);
        cache.put("b", RESULT);
        cache.get("a");          // "a" redevient la plus récente
        cache.put("c", RESULT);  // évince "b"

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.get("a")).isPresent();
        assertThat(cache.get("b")).isEmpty();
        assertThat(cache.get("c")).isPresent();
    }

    @Test
    void zeroLifetimeDisablesTheCache() {
        AnalysisCache cache = new AnalysisCache(0, 200);
        cache.put("k", RESULT);

        assertThat(cache.enabled()).isFalse();
        assertThat(cache.get("k")).isEmpty();
    }

    @Test
    void cachedResultCannotBeModifiedAfterwards() {
        List<String> strengths = new ArrayList<>(List.of("Java"));
        AnalysisCache cache = new AnalysisCache(Duration.ofMinutes(60), 10, Clock.systemUTC());
        cache.put("k", new AnalysisResult(70, "s", strengths, List.of(), List.of()));

        strengths.add("Ajout après coup");

        assertThat(cache.get("k")).hasValueSatisfying(r -> assertThat(r.strengths()).containsExactly("Java"));
    }
}
