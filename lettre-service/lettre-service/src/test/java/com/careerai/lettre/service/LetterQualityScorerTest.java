package com.careerai.lettre.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Évaluation chiffrée automatique d'une lettre : contraintes du prompt et hallucinations mesurables. */
class LetterQualityScorerTest {

    private static final String CV = "Développeur Java chez Acme (2021 - 2023). Compétences : Java, Spring Boot, MySQL, Docker, Git.";
    private static final String OFFRE = "Nous cherchons un développeur Java Spring Boot. Kubernetes et AWS appréciés. Docker indispensable.";

    /** Paragraphe neutre de 54 mots, sans compétence ni nombre. */
    private static final String PARA = "Je vous écris pour vous faire part de mon intérêt pour le poste de développeur au sein de "
            + "votre entreprise et pour vous présenter mon parcours dans le développement logiciel avec une grande motivation "
            + "pour rejoindre vos équipes techniques et contribuer à vos projets les plus ambitieux dans les années à venir, "
            + "avec rigueur, curiosité et un véritable esprit d'équipe au quotidien.";

    private static String goodLetter() {
        return "Objet : Candidature au poste de Développeur Java\n\nMadame, Monsieur,\n\n"
                + PARA + " Chez Acme, j'ai utilisé Java et Spring Boot.\n\n"
                + PARA + " J'ai aussi travaillé avec Docker et MySQL.\n\n"
                + PARA + " Je serais ravi de rejoindre Globex.\n\n"
                + PARA + "\n\nVeuillez agréer, Madame, Monsieur, mes salutations distinguées.\n\n[Prénom Nom]";
    }

    @Test
    void aLetterRespectingThePromptGetsAllChecksAndNoHallucination() {
        LetterQualityScorer.Report r = LetterQualityScorer.evaluate(goodLetter(), CV, OFFRE, "Globex", "Développeur Java", "fr");

        assertThat(r.words()).isBetween(250, 350);
        assertThat(r.paragraphs()).isEqualTo(4);
        assertThat(r.checks()).allSatisfy((name, ok) -> assertThat(ok).as(name).isTrue());
        assertThat(r.constraintScore()).isEqualTo(100.0);
        assertThat(r.expectedSkills()).containsExactly("java", "spring boot", "docker");
        assertThat(r.offerCoverage()).isEqualTo(100.0);
        assertThat(r.hallucinationSuspected()).isFalse();
        assertThat(r.autoScore()).isEqualTo(100.0);
    }

    @Test
    void skillsRequiredByTheOfferButAbsentFromTheCvAreFlaggedAsInvented() {
        String letter = goodLetter().replace("Docker et MySQL", "Kubernetes et AWS");

        LetterQualityScorer.Report r = LetterQualityScorer.evaluate(letter, CV, OFFRE, "Globex", null, "fr");

        assertThat(r.unsupportedSkills()).containsExactlyInAnyOrder("kubernetes", "aws");
        assertThat(r.hallucinationSuspected()).isTrue();
        assertThat(r.offerCoverage()).isLessThan(100.0); // Docker n'est plus cité
    }

    @Test
    void numbersAbsentFromTheCvAndTheOfferAreFlagged() {
        String letter = goodLetter().replace("j'ai utilisé Java", "j'ai utilisé Java pendant 5 ans en 2023");

        LetterQualityScorer.Report r = LetterQualityScorer.evaluate(letter, CV, OFFRE, "Globex", null, "fr");

        assertThat(r.unsupportedNumbers()).containsExactly("5"); // 2023 est dans le CV
        assertThat(r.hallucinationSuspected()).isTrue();
    }

    @Test
    void formatViolationsAreEachDetected() {
        String bad = "**Objet** : test\nJ'ai travaillé chez Acme. Contact : sami@example.com, +216 22 123 456 [[EMAIL_1]]";

        LetterQualityScorer.Report r = LetterQualityScorer.evaluate(bad, CV, OFFRE, "Globex", null, "fr");

        assertThat(r.checks().get("longueur_250_350_mots")).isFalse();
        assertThat(r.checks().get("structure_3_4_paragraphes")).isFalse();
        assertThat(r.checks().get("signature_a_completer")).isFalse();
        assertThat(r.checks().get("sans_markdown")).isFalse();
        assertThat(r.checks().get("sans_jeton_technique")).isFalse();
        assertThat(r.checks().get("sans_coordonnees")).isFalse();
        assertThat(r.checks().get("entreprise_citee")).isFalse();
        assertThat(r.unsupportedNumbers()).doesNotContain("1"); // le chiffre du jeton n'est pas un fait inventé
        assertThat(r.constraintScore()).isLessThan(30.0);
    }

    @Test
    void languageIsChecked() {
        String english = "Subject: application. Dear hiring manager, I am writing to apply for the role and I have "
                + "experience with the team and my skills fit your needs for the position.";
        assertThat(LetterQualityScorer.evaluate(english, "", "", null, null, "en").checks().get("langue_respectee")).isTrue();
        assertThat(LetterQualityScorer.evaluate(english, "", "", null, null, "fr").checks().get("langue_respectee")).isFalse();
    }

    @Test
    void ambiguousSkillWordsAreMatchedCaseSensitivelyAndExactly() {
        assertThat(LetterQualityScorer.skillsIn("I react quickly and excel at teamwork; I go fast."))
                .isEmpty();
        assertThat(LetterQualityScorer.skillsIn("JavaScript, React, C#, .NET et C++"))
                .containsExactlyInAnyOrder("javascript", "react", "c#", ".net", "c++");
        assertThat(LetterQualityScorer.skillsIn("JavaScript")).doesNotContain("java");
        assertThat(LetterQualityScorer.skillsIn("NodeJS et microservice")).containsExactlyInAnyOrder("node.js", "microservices");
    }

    @Test
    void whenTheCvCoversNoSkillOfTheOfferCoverageIsNotPenalised() {
        LetterQualityScorer.Report r = LetterQualityScorer.evaluate(goodLetter(), "Vendeur en boutique", "Poste de caissier",
                "Globex", null, "fr");
        assertThat(r.expectedSkills()).isEmpty();
        assertThat(r.offerCoverage()).isEqualTo(100.0);
    }
}
