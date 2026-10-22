package com.careerai.skillgap.service;

import com.careerai.skillgap.dto.AnalysisResult;
import com.careerai.skillgap.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exigence du sujet : la sortie du LLM est validée avant affichage. */
class SkillGapParseTest {

    private final SkillGapService service = new SkillGapService(null, null, null, null, null, new ObjectMapper(), null);

    @Test
    void jsonWrappedInMarkdownIsAccepted() {
        AnalysisResult r = service.parse("""
                Voici l'analyse :
                ```json
                {"matchScore": 72, "summary": "Bon profil.", "strengths": ["Java"],
                 "missingSkills": [{"skill": "Docker", "priority": "high", "reason": "r", "howToImprove": "h"}],
                 "cvTips": ["Quantifier les résultats"]}
                ```
                """);
        assertThat(r.matchScore()).isEqualTo(72);
        assertThat(r.missingSkills()).singleElement()
                .satisfies(m -> assertThat(m.priority()).isEqualTo("HIGH"));
    }

    @Test
    void outOfRangeValuesAreNormalized() {
        AnalysisResult r = service.parse("""
                {"matchScore": 140, "summary": "s", "strengths": ["Java", {"x": 1}, ""],
                 "missingSkills": [{"skill": "Docker", "priority": "URGENT"}, "pas un objet", {"skill": ""}],
                 "cvTips": []}
                """);
        assertThat(r.matchScore()).isEqualTo(100);
        assertThat(r.strengths()).containsExactly("Java");
        assertThat(r.missingSkills()).singleElement()
                .satisfies(m -> assertThat(m.priority()).isEqualTo("MEDIUM"));
    }

    @Test
    void maskedTokensNeverReachTheUser() {
        AnalysisResult r = service.parse("""
                {"matchScore": 50, "summary": "Contactez [[EMAIL_1]] ou [[PHONE_2]].", "strengths": ["[[URL_1]]"],
                 "missingSkills": [], "cvTips": ["Ajoutez [[URL_1]] au CV"]}
                """);
        assertThat(r.summary()).doesNotContain("[[");
        assertThat(r.strengths()).isEmpty();
        assertThat(r.cvTips()).singleElement().asString().doesNotContain("[[");
    }

    /** Défaut relevé par la comparaison de modèles : gpt-oss-120b renvoie au « contexte » que le candidat ne voit pas. */
    @Test
    void internalPromptReferencesAreRemoved() {
        AnalysisResult r = service.parse("""
                {"matchScore": 40, "summary": "s",
                 "missingSkills": [{"skill": "Docker", "priority": "HIGH", "reason": "r",
                   "howToImprove": "Suivre le parcours Docker du contexte et obtenir la certification Docker Certified Associate."},
                  {"skill": "CI/CD", "priority": "MEDIUM", "reason": "r",
                   "howToImprove": "Créer un pipeline GitLab CI en suivant le parcours CI/CD du contexte."}],
                 "cvTips": ["Mettre en avant les projets cités dans la fiche."]}
                """);
        assertThat(r.missingSkills()).extracting(m -> m.howToImprove()).containsExactly(
                "Suivre le parcours Docker et obtenir la certification Docker Certified Associate.",
                "Créer un pipeline GitLab CI en suivant le parcours CI/CD.");
        assertThat(r.cvTips()).containsExactly("Mettre en avant les projets cités.");
    }

    /** Le mot « contexte » employé normalement n'est pas touché. */
    @Test
    void ordinaryUseOfTheWordContextIsKept() {
        AnalysisResult r = service.parse("""
                {"matchScore": 40, "summary": "Expérience Java dans un contexte bancaire exigeant.", "strengths": ["Java"],
                 "missingSkills": [], "cvTips": []}
                """);
        assertThat(r.summary()).isEqualTo("Expérience Java dans un contexte bancaire exigeant.");
    }

    @Test
    void listsAndTextsAreCapped() {
        String skills = "{\"skill\": \"S\", \"priority\": \"LOW\"},".repeat(15);
        AnalysisResult r = service.parse("{\"matchScore\": 10, \"summary\": \"" + "a".repeat(5000) + "\","
                + "\"missingSkills\": [" + skills.substring(0, skills.length() - 1) + "]}");
        assertThat(r.missingSkills()).hasSize(10);
        assertThat(r.summary()).hasSize(1000).endsWith("…");
    }

    @Test
    void nonJsonAnswerIsRejected() {
        assertThatThrownBy(() -> service.parse("Désolé, je ne peux pas répondre."))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void emptyJsonIsRejected() {
        assertThatThrownBy(() -> service.parse("{\"matchScore\": 50}")).isInstanceOf(ApiException.class);
    }
}
