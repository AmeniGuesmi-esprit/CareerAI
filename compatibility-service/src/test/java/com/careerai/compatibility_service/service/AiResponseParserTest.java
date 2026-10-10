package com.careerai.compatibility_service.service;

import com.careerai.compatibility_service.dto.AnalysisDtos.AiResult;
import com.careerai.compatibility_service.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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
}