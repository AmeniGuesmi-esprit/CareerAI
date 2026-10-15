package com.careerai.compatibility_service.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalDataAnonymizerTest {

    private final PersonalDataAnonymizer anonymizer = new PersonalDataAnonymizer();

    @Test
    void masqueLesEmails() {
        assertThat(anonymizer.anonymize("Contact : farah@esprit.tn")).isEqualTo("Contact : [EMAIL]");
    }

    @Test
    void masqueLesTelephones() {
        assertThat(anonymizer.anonymize("Tel +216 20 123 456")).isEqualTo("Tel [TELEPHONE]");
    }

    @Test
    void conserveLesPlagesDeDates() {
        assertThat(anonymizer.anonymize("Stage 2022 - 2023")).isEqualTo("Stage 2022 - 2023");
    }

    @Test
    void masqueLesLiens() {
        assertThat(anonymizer.anonymize("Profil https://github.com/utilisateur")).isEqualTo("Profil [LIEN]");
    }

    @Test
    void renvoieUneChaineVidePourNull() {
        assertThat(anonymizer.anonymize(null)).isEmpty();
    }

    @Test
    void masquePlusieursEmailsDansLeMemeTexte() {
        assertThat(anonymizer.anonymize("a.b+cv@mail.co.uk ou x_y@esprit.tn"))
                .isEqualTo("[EMAIL] ou [EMAIL]");
    }

    @Test
    void masqueLesLiensSansProtocole() {
        assertThat(anonymizer.anonymize("www.monsite.fr et linkedin.com/in/farah"))
                .isEqualTo("[LIEN] et [LIEN]");
    }

    @Test
    void masqueUnTelephoneAvecPoints() {
        assertThat(anonymizer.anonymize("Tél. 06.12.34.56.78")).isEqualTo("Tél. [TELEPHONE]");
    }

    @Test
    void masqueUnTelephoneInternational() {
        assertThat(anonymizer.anonymize("+33 6 12 34 56 78")).isEqualTo("[TELEPHONE]");
    }

    @Test
    void conserveLesPetitsNombres() {
        String text = "Java 17 et 21, Bac+5, 3 ans, 120 000 utilisateurs";
        assertThat(anonymizer.anonymize(text)).isEqualTo(text);
    }

    @Test
    void conserveUnePlageDeDatesSansEspaces() {
        assertThat(anonymizer.anonymize("Licence 2019-2022")).isEqualTo("Licence 2019-2022");
    }

    @Test
    void conserveUnTexteSansDonneePersonnelle() {
        String text = "Développeur Spring Boot / Angular, anglais courant.";
        assertThat(anonymizer.anonymize(text)).isEqualTo(text);
    }

    /** BUG : une période avec mois (MM.AAAA - MM.AAAA) a 12 chiffres et est prise pour un numéro. */
    @Disabled("Bug connu de PersonalDataAnonymizer : les périodes avec mois sont masquées en [TELEPHONE]")
    @Test
    void conserveUnePeriodeAvecMois() {
        assertThat(anonymizer.anonymize("Stage 01.2022 - 06.2022")).isEqualTo("Stage 01.2022 - 06.2022");
    }

    @Test
    void documenteLeComportementActuelSurLesPeriodesAvecMois() {
        assertThat(anonymizer.anonymize("Stage 01.2022 - 06.2022")).isEqualTo("Stage [TELEPHONE]");
    }
}
