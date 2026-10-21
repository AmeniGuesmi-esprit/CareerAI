package com.careerai.competence.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalDataMaskerTest {

    private static String mask(String s) {
        return PersonalDataMasker.mask(s).text();
    }

    @Test
    void email_isMasked() {
        assertThat(mask("Écris-moi à karim.mansouri@gmail.com pour la suite"))
                .isEqualTo("Écris-moi à [EMAIL] pour la suite");
    }

    @Test
    void links_areMasked() {
        assertThat(mask("Mon profil : https://www.linkedin.com/in/karim et github.com/karim42"))
                .isEqualTo("Mon profil : [LIEN] et [LIEN]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"+216 22 123 456", "+21622123456", "0033 6 12 34 56 78", "06 12 34 56 78", "22 123 456", "98123456"})
    void phones_areMasked(String phone) {
        assertThat(mask("Appelle-moi au " + phone + " demain")).isEqualTo("Appelle-moi au [TÉLÉPHONE] demain");
    }

    @Test
    void iban_card_andIdentityNumber_areMasked() {
        assertThat(mask("IBAN TN59 1000 6035 1835 9847 8831")).isEqualTo("IBAN [IBAN]");
        assertThat(mask("carte 4970 1234 5678 9012")).isEqualTo("carte [CARTE]");
        assertThat(mask("Ma CIN 12345678 est valide")).isEqualTo("Ma CIN [IDENTIFIANT] est valide");
    }

    @Test
    void nameIntroducedByAPhrase_isMasked() {
        assertThat(mask("Bonjour, je m'appelle Karim Mansouri et je suis étudiant"))
                .isEqualTo("Bonjour, je m'appelle [NOM] et je suis étudiant");
        assertThat(mask("Nom : Ben Ali")).isEqualTo("Nom : [NOM]");
    }

    @Test
    void addressAndBirthDate_areMasked() {
        assertThat(mask("J'habite au 12 rue de Carthage, à Tunis")).isEqualTo("J'habite au [ADRESSE], à Tunis");
        assertThat(mask("Je suis né le 12/03/2001")).isEqualTo("Je suis né le [DATE]");
    }

    /** Le parcours doit rester intact : c'est ce dont le conseiller a besoin. */
    @ParameterizedTest
    @ValueSource(strings = {
            "Je suis en 4e année d'ingénierie, de 2021 à 2024 (2021-2024).",
            "Je connais Java 17, Spring Boot 3.2 et Angular 21.",
            "Format : 5 points maximum, réponse de 150 à 250 mots.",
            "Quelles certifications : AWS Certified Cloud Practitioner ou AZ-900 ?",
            "Salaire visé 2 500 dinars, bac+5, moyenne 14,5 / 20.",
            "Mon chemin vers la data science, moi c'est Python que je préfère."
    })
    void careerInformation_isKeptUnchanged(String text) {
        PersonalDataMasker.Result r = PersonalDataMasker.mask(text);
        assertThat(r.text()).isEqualTo(text);
        assertThat(r.count()).isZero();
    }

    @Test
    void count_reportsEveryReplacement() {
        PersonalDataMasker.Result r = PersonalDataMasker.mask("a@b.tn, +216 22 123 456 et www.site.tn");
        assertThat(r.count()).isEqualTo(3);
    }

    @Test
    void nullOrEmpty_isSafe() {
        assertThat(mask(null)).isEmpty();
        assertThat(mask("")).isEmpty();
    }
}
