package com.careerai.skillgap.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Exigence du sujet : jamais de données personnelles brutes envoyées telles quelles au LLM. */
class PiiMaskerTest {

    private final PiiMasker masker = new PiiMasker();

    private static final String CV = """
            Sami Trabelsi
            Ingénieur logiciel
            sami.trabelsi@example.com | +216 22 123 456
            12 rue de Marseille
            1002 Tunis
            linkedin.com/in/sami-trabelsi
            Expérience
            2021 - 2023 Développeur Java chez Sofrecom
            Sami Trabelsi a conçu une API REST Spring Boot.
            """;

    @Test
    void contactDataIsReplacedByTokens() {
        String out = masker.anonymizeCv(CV);
        assertThat(out)
                .doesNotContain("sami.trabelsi@example.com", "+216 22 123 456", "linkedin.com/in/sami-trabelsi")
                .contains("[[EMAIL_1]]", "[[PHONE_1]]", "[[URL_1]]");
    }

    @Test
    void nameIsRemovedEverywhere() {
        assertThat(masker.anonymizeCv(CV)).doesNotContainIgnoringCase("Sami").doesNotContainIgnoringCase("Trabelsi");
    }

    @Test
    void postalAddressIsRemoved() {
        assertThat(masker.anonymizeCv(CV)).doesNotContain("rue de Marseille", "1002 Tunis");
    }

    @Test
    void professionalContentIsKept() {
        assertThat(masker.anonymizeCv(CV))
                .contains("2021 - 2023", "Développeur Java chez Sofrecom", "API REST Spring Boot");
    }

    @Test
    void jobOfferContactIsMasked() {
        String out = masker.mask("Envoyez votre candidature à rh@entreprise.tn ou au 71 123 456.").text();
        assertThat(out).doesNotContain("rh@entreprise.tn", "71 123 456");
    }
}
