package com.careerai.compatibility_service.service;

import com.careerai.compatibility_service.dto.AnalysisDtos.AiResult;
import com.careerai.compatibility_service.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiResponseParserTest {

    private final AiResponseParser parser = new AiResponseParser(new ObjectMapper());

    @Test
    void accepteUnJsonValideEntoureDeMarkdown() {
        String raw = "```json\n{\"score\":72,\"resume\":\"Bon profil.\",\"points_forts\":[\"Spring Boot\"],"
                + "\"competences_manquantes\":[\"Kubernetes\"],\"suggestions\":[\"Ajouter un projet CI/CD\"]}\n```";
        AiResult result = parser.parse(raw);
        assertThat(result.score()).isEqualTo(72);
        assertThat(result.missingSkills()).containsExactly("Kubernetes");
    }

    @Test
    void rejetteUnScoreHorsIntervalle() {
        String raw = "{\"score\":150,\"resume\":\"x\"}";
        assertThatThrownBy(() -> parser.parse(raw))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("AI_INVALID_RESPONSE");
    }

    @Test
    void rejetteUneReponseSansJson() {
        assertThatThrownBy(() -> parser.parse("Désolé, je ne peux pas."))
                .isInstanceOf(ApiException.class);
    }

    private void assertInvalid(String raw, String detail) {
        assertThatThrownBy(() -> parser.parse(raw))
                .isInstanceOf(ApiException.class)
                .hasMessage("La réponse de l'IA est invalide (" + detail + ").")
                .extracting("code", "status")
                .containsExactly("AI_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY);
    }

    @Test
    void rejetteUneReponseNulle() {
        assertInvalid(null, "réponse vide");
    }

    @Test
    void rejetteUnScoreNegatif() {
        assertInvalid("{\"score\":-1,\"resume\":\"x\"}", "score hors de l'intervalle 0-100");
    }

    @Test
    void rejetteUnScoreAbsent() {
        assertInvalid("{\"resume\":\"x\"}", "score hors de l'intervalle 0-100");
    }

    @Test
    void accepteLesBornes0Et100() {
        assertThat(parser.parse("{\"score\":0,\"resume\":\"x\"}").score()).isZero();
        assertThat(parser.parse("{\"score\":100,\"resume\":\"x\"}").score()).isEqualTo(100);
    }

    @Test
    void rejetteUnResumeVide() {
        assertInvalid("{\"score\":50,\"resume\":\"   \"}", "résumé manquant");
    }

    @Test
    void rejetteUnJsonMalForme() {
        assertInvalid("{\"score\": , \"resume\": }", "JSON mal formé");
    }

    @Test
    void rejetteDesAccoladesDansLeMauvaisOrdre() {
        assertInvalid("} texte {", "aucun JSON trouvé");
    }

    @Test
    void accepteDuTexteAvecAccoladesApresLeJson() {
        AiResult r = parser.parse("{\"score\":40,\"resume\":\"x\"} Remarque : {fin}");
        assertThat(r.score()).isEqualTo(40);
    }

    @Test
    void remplaceLesListesAbsentesParDesListesVides() {
        AiResult r = parser.parse("{\"score\":50,\"resume\":\"x\"}");
        assertThat(r.strengths()).isEmpty();
        assertThat(r.missingSkills()).isEmpty();
        assertThat(r.suggestions()).isEmpty();
    }

    @Test
    void nettoieLesListesElementsVidesEspacesEtLimiteDeHuit() {
        String items = "[\"  Java  \", \"\", null, \"   \", \"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\"]";
        AiResult r = parser.parse("{\"score\":50,\"resume\":\"x\",\"points_forts\":" + items + "}");
        assertThat(r.strengths()).containsExactly("Java", "2", "3", "4", "5", "6", "7", "8");
    }

    @Test
    void tronqueLeResumeA600EtLesElementsA300Caracteres() {
        String raw = "{\"score\":50,\"resume\":\"  " + "r".repeat(700) + "  \",\"suggestions\":[\""
                + "s".repeat(400) + "\"]}";
        AiResult r = parser.parse(raw);
        assertThat(r.summary()).isEqualTo("r".repeat(600));
        assertThat(r.suggestions()).containsExactly("s".repeat(300));
    }

    @Test
    void ignoreLesCompetencesVerifieesInventeesParLeModele() {
        AiResult r = parser.parse("{\"score\":50,\"resume\":\"x\",\"competences_verifiees\":[\"Kubernetes\"]}");
        assertThat(r.verifiedSkills()).isEmpty();
    }

    @Test
    void ignoreLesChampsInconnus() {
        assertThat(parser.parse("{\"score\":50,\"resume\":\"x\",\"autre\":true}").summary()).isEqualTo("x");
    }

    @Test
    void valideUneEntreeDOutil() throws Exception {
        JsonNode input = new ObjectMapper().readTree("{\"score\":65,\"resume\":\" Ok \",\"suggestions\":[\"a\"]}");
        AiResult r = parser.fromToolInput(input);
        assertThat(r.score()).isEqualTo(65);
        assertThat(r.summary()).isEqualTo("Ok");
        assertThat(r.suggestions()).isEqualTo(List.of("a"));
    }

    @Test
    void rejetteUneEntreeDOutilQuiNEstPasUnObjet() throws Exception {
        JsonNode array = new ObjectMapper().readTree("[1,2]");
        assertThatThrownBy(() -> parser.fromToolInput(array))
                .isInstanceOf(ApiException.class)
                .hasMessage("La réponse de l'IA est invalide (entrée d'outil absente).");
        assertThatThrownBy(() -> parser.fromToolInput(null))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("AI_INVALID_RESPONSE");
    }
}
