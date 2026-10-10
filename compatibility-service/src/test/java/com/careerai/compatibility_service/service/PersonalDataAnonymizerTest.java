package com.careerai.compatibility_service.service;

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
}