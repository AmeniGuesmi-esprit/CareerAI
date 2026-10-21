package com.careerai.compatibility_service.service;

import org.junit.jupiter.api.Test;

import static com.careerai.compatibility_service.service.ModelEvaluationTest.label;
import static com.careerai.compatibility_service.service.ModelEvaluationTest.mentions;
import static com.careerai.compatibility_service.service.ModelEvaluationTest.normalize;
import static org.assertj.core.api.Assertions.assertThat;

/** Vérifie les règles utilisées par l'évaluation des modèles (sinon les indicateurs seraient faux). */
class EvaluationMatchingTest {

    @Test
    void trouveUneVarianteSansCasseNiAccents() {
        assertThat(mentions(normalize("Intégration continue, Kubernetes"), "integration continue|ci")).isTrue();
        assertThat(mentions(normalize("Maîtrise de KUBERNETES"), "kubernetes|k8s")).isTrue();
    }

    @Test
    void exigeUnMotEntier() {
        assertThat(mentions(normalize("Tests unitaires Jest"), "ts")).isFalse();
        assertThat(mentions(normalize("Spécialisation"), "ci")).isFalse();
        assertThat(mentions(normalize("Java / TS"), "ts")).isTrue();
    }

    @Test
    void accepteLesLibellesAvecPonctuation() {
        assertThat(mentions(normalize("Mise en place de CI/CD avec GitLab"), "ci/cd")).isTrue();
        assertThat(mentions(normalize("Power BI avancé"), "power bi")).isTrue();
    }

    @Test
    void nommeLaConfigurationSelonLesParametresCompares() {
        assertThat(label("qwen2.5:7b", "v1", 0.2, 1, 1)).isEqualTo("qwen2.5:7b");
        assertThat(label("qwen2.5:7b", "v2", 0.2, 2, 1)).isEqualTo("qwen2.5:7b · prompt v2");
        assertThat(label("qwen2.5:3b", "v1", 0.7, 1, 3)).isEqualTo("qwen2.5:3b · T=0.7");
    }
}
