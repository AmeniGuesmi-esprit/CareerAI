package com.careerai.lettre.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Anonymisation avant l'envoi au LLM : e-mails, téléphones, liens masqués ; nom et adresse retirés. */
class PiiMaskerTest {

    private final PiiMasker masker = new PiiMasker();

    private static final String CV = """
            Sami Trabelsi
            sami.trabelsi@example.com | +216 22 123 456
            12 rue de Marseille
            1002 Tunis

            Expérience
            Développeur Java chez Acme (2021 - 2023)
            Compétences : Java, Spring Boot, MySQL
            """;

    @Test
    void masksEmail() {
        PiiMasker.Masked m = masker.mask("Contact : a.b@mail.com");
        assertThat(m.text()).isEqualTo("Contact : [[EMAIL_1]]");
        assertThat(m.tokens()).containsEntry("[[EMAIL_1]]", "a.b@mail.com");
    }

    @Test
    void masksPhone() {
        PiiMasker.Masked m = masker.mask("Tél : +216 22 123 456");
        assertThat(m.text()).contains("[[PHONE_1]]");
        assertThat(m.text()).doesNotContain("123 456");
    }

    @Test
    void masksLinks() {
        PiiMasker.Masked m = masker.mask("https://github.com/sami et linkedin.com/in/sami");
        assertThat(m.tokens()).hasSize(2);
        assertThat(m.text()).doesNotContain("github.com");
        assertThat(m.text()).doesNotContain("linkedin.com");
    }

    @Test
    void yearRangeIsNotAPhoneNumber() {
        String text = "Développeur Java 2021 - 2023 puis 2019-2021";
        PiiMasker.Masked m = masker.mask(text);
        assertThat(m.text()).isEqualTo(text);
        assertThat(m.tokens()).isEmpty();
    }

    @Test
    void restoreGivesBackTheOriginalText() {
        String original = "Écrire à a.b@mail.com ou appeler +216 22 123 456";
        PiiMasker.Masked m = masker.mask(original);
        assertThat(m.text()).isNotEqualTo(original);
        assertThat(masker.restore(m.text(), m.tokens())).isEqualTo(original);
    }

    @Test
    void detectsAMissingToken() {
        Map<String, String> tokens = masker.mask("a.b@mail.com").tokens();
        assertThat(masker.hasMissingToken("texte sans jeton", tokens)).isTrue();
        assertThat(masker.hasMissingToken("voir [[EMAIL_1]]", tokens)).isFalse();
    }

    @Test
    void anonymizeCvRemovesNameEmailPhoneAndAddress() {
        String out = masker.anonymizeCv(CV);

        assertThat(out).doesNotContain("Sami");
        assertThat(out).doesNotContain("Trabelsi");
        assertThat(out).doesNotContain("sami.trabelsi");
        assertThat(out).doesNotContain("example.com");
        assertThat(out).doesNotContain("22 123 456");
        assertThat(out).doesNotContain("rue de Marseille");
        assertThat(out).doesNotContain("1002 Tunis");
    }

    @Test
    void anonymizeCvKeepsTheProfessionalContent() {
        String out = masker.anonymizeCv(CV);

        assertThat(out).contains("[[EMAIL_1]]");
        assertThat(out).contains("[[PHONE_1]]");
        assertThat(out).contains("Développeur Java chez Acme (2021 - 2023)");
        assertThat(out).contains("Compétences : Java, Spring Boot, MySQL");
    }

    @Test
    void anonymizeCvHandlesLabelledNameAndAddress() {
        String cv = """
                Nom : Leila Ben Ali
                Adresse : 5 avenue Habib Bourguiba, Tunis
                Compétences : Python, SQL
                """;
        String out = masker.anonymizeCv(cv);

        assertThat(out).doesNotContain("Leila");
        assertThat(out).doesNotContain("Ben Ali");
        assertThat(out).doesNotContain("Bourguiba");
        assertThat(out).contains("Compétences : Python, SQL");
    }

    @Test
    void sectionTitlesAreNotTakenForAName() {
        String out = masker.anonymizeCv("Curriculum Vitae\nCompétences : Java\n");
        assertThat(out).contains("Curriculum Vitae");
        assertThat(out).contains("Compétences : Java");
    }

    @Test
    void anonymizeCvOfNullIsEmpty() {
        assertThat(masker.anonymizeCv(null)).isEmpty();
    }
}
