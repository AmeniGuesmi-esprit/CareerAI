package com.careerai.skillgap.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Filtre du RAG : une fiche n'est écartée que si le CV démontre déjà la compétence. */
class CvCoverageTest {

    private final CvCoverage cv = new CvCoverage("""
            Compétences : Java, Spring Boot, Docker, Prometheus
            Notions de Kubernetes
            """);

    @Test
    void demonstratedSkillIsCovered() {
        assertThat(cv.covers("Docker")).isTrue();
    }

    @Test
    void beginnerLevelIsNotCovered() {
        assertThat(cv.covers("Kubernetes")).isFalse();
    }

    @Test
    void partiallyDemonstratedSkillIsNotCovered() {
        assertThat(cv.covers("Supervision (Prometheus, Grafana)")).isFalse();
    }
}
