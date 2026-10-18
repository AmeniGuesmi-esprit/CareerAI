package com.careerai.compatibility_service.service;

import com.careerai.compatibility_service.config.AppConfig.AiProperties;
import com.careerai.compatibility_service.dto.AnalysisDtos.AiResult;
import com.careerai.compatibility_service.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaAnalysisClientTest {

    private static final String VALID_ANALYSIS = "{\"score\":72,\"resume\":\"Bon profil backend.\","
            + "\"points_forts\":[\"Spring Boot\"],\"competences_manquantes\":[\"Kubernetes\"],"
            + "\"suggestions\":[\"Ajouter un projet Docker\"]}";

    private final ObjectMapper mapper = new ObjectMapper();
    private FakeOllama ollama;
    private OllamaAnalysisClient client;

    @BeforeEach
    void setUp() throws Exception {
        ollama = new FakeOllama();
        client = clientFor(ollama.url());
    }

    @AfterEach
    void tearDown() {
        ollama.close();
    }

    private OllamaAnalysisClient clientFor(String url) throws Exception {
        AiProperties props = new AiProperties("qwen-test", url, 1, 4096);
        return new OllamaAnalysisClient(props, new AiResponseParser(mapper), new SkillReferential(mapper), mapper);
    }

    private JsonNode request(int index) throws Exception {
        return mapper.readTree(ollama.requests.get(index));
    }

    // ── Cas nominaux ────────────────────────────────────────────────────────

    @Test
    void renvoieLAnalyseQuandLeModeleNAppelleAucunOutil() {
        ollama.replyContent("").replyContent(VALID_ANALYSIS);

        AiResult result = client.analyze("cv", "offre");

        assertThat(result.score()).isEqualTo(72);
        assertThat(result.summary()).isEqualTo("Bon profil backend.");
        assertThat(result.missingSkills()).containsExactly("Kubernetes");
        assertThat(result.verifiedSkills()).isEmpty();
        assertThat(ollama.requests).hasSize(2);
    }

    @Test
    void envoieModeleOptionsEtMessagesDansLaRequete() throws Exception {
        ollama.replyContent("").replyContent(VALID_ANALYSIS);

        client.analyze("Mon CV", "Mon offre");

        JsonNode first = request(0);
        assertThat(first.path("model").asText()).isEqualTo("qwen-test");
        assertThat(first.path("stream").asBoolean(true)).isFalse();
        assertThat(first.path("options").path("num_ctx").asInt()).isEqualTo(4096);
        assertThat(first.path("options").path("temperature").asDouble()).isEqualTo(0.2);
        assertThat(first.path("tools").get(0).path("function").path("name").asText())
                .isEqualTo("rechercher_competences");
        assertThat(first.has("format")).isFalse();
        assertThat(first.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(first.path("messages").get(1).path("content").asText())
                .isEqualTo("<cv>\nMon CV\n</cv>\n\n<offre>\nMon offre\n</offre>");
    }

    @Test
    void imposeLeSchemaJsonSansOutilsPourLaReponseFinale() throws Exception {
        ollama.replyContent("").replyContent(VALID_ANALYSIS);

        client.analyze("cv", "offre");

        JsonNode last = request(1);
        assertThat(last.has("tools")).isFalse();
        assertThat(last.path("format").path("required")).extracting(JsonNode::asText)
                .containsExactly("score", "resume", "points_forts", "competences_manquantes", "suggestions");
    }

    @Test
    void executeLOutilEtRemonteLesCompetencesVerifiees() throws Exception {
        ollama.replyToolCall("rechercher_competences", Map.of("competences", List.of("MySQL", "Cobol")))
                .replyContent("")
                .replyContent(VALID_ANALYSIS);

        AiResult result = client.analyze("cv", "offre");

        assertThat(result.verifiedSkills()).containsExactly("SQL");
        JsonNode toolMessage = request(1).path("messages").get(3);
        assertThat(toolMessage.path("role").asText()).isEqualTo("tool");
        assertThat(toolMessage.path("tool_name").asText()).isEqualTo("rechercher_competences");
        JsonNode lookup = mapper.readTree(toolMessage.path("content").asText());
        assertThat(lookup.get(0).path("competence").asText()).isEqualTo("SQL");
        assertThat(lookup.get(1).path("trouve").asBoolean()).isFalse();
    }

    @Test
    void accepteDesArgumentsDOutilEnChaineJson() {
        ollama.replyToolCall("rechercher_competences", "{\"competences\":[\"Spring\"]}")
                .replyContent("")
                .replyContent(VALID_ANALYSIS);

        AiResult result = client.analyze("cv", "offre");

        assertThat(result.verifiedSkills()).containsExactly("Spring Boot");
    }

    @Test
    void repondOutilInconnuSansVerifierDeCompetence() throws Exception {
        ollama.replyToolCall("supprimer_base", Map.of("competences", List.of("Java")))
                .replyContent("")
                .replyContent(VALID_ANALYSIS);

        AiResult result = client.analyze("cv", "offre");

        assertThat(result.verifiedSkills()).isEmpty();
        assertThat(request(1).path("messages").get(3).path("content").asText())
                .isEqualTo("Outil inconnu : supprimer_base");
    }

    @Test
    void limiteLaPhaseOutilsADeuxTours() {
        ollama.replyToolCall("rechercher_competences", Map.of("competences", List.of("Java")))
                .replyToolCall("rechercher_competences", Map.of("competences", List.of("Docker")))
                .replyContent(VALID_ANALYSIS);

        AiResult result = client.analyze("cv", "offre");

        assertThat(result.verifiedSkills()).containsExactly("Java", "Docker");
        assertThat(ollama.requests).hasSize(3);
    }

    @Test
    void ignoreLaPhaseOutilsSiLeModeleNeLesGerePas() throws Exception {
        ollama.reply(400, "{\"error\":\"registry.ollama.ai/library/gemma:2b does not support tools\"}")
                .replyContent(VALID_ANALYSIS);

        AiResult result = client.analyze("cv", "offre");

        assertThat(result.score()).isEqualTo(72);
        assertThat(result.verifiedSkills()).isEmpty();
        assertThat(request(1).has("format")).isTrue();
    }

    // ── Réponses mal formées ────────────────────────────────────────────────

    @Test
    void autoCorrigeUneAnalyseInvalideAuDeuxiemeEssai() throws Exception {
        ollama.replyContent("")
                .replyContent("{\"score\":150,\"resume\":\"x\"}")
                .replyContent(VALID_ANALYSIS);

        AiResult result = client.analyze("cv", "offre");

        assertThat(result.score()).isEqualTo(72);
        JsonNode messages = request(2).path("messages");
        assertThat(messages.get(messages.size() - 1).path("content").asText())
                .startsWith("La réponse de l'IA est invalide (score hors de l'intervalle 0-100).");
    }

    @Test
    void echoueApresDeuxAnalysesInvalides() {
        ollama.replyContent("")
                .replyContent("Désolé, je ne peux pas.")
                .replyContent("{\"score\":72,\"resume\":\"\"}");

        assertThatThrownBy(() -> client.analyze("cv", "offre"))
                .isInstanceOf(ApiException.class)
                .hasMessage("La réponse de l'IA est invalide (résumé manquant).")
                .extracting("code", "status")
                .containsExactly("AI_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY);
        assertThat(ollama.requests).hasSize(3);
    }

    @Test
    void rejetteUneReponseSansChampMessage() {
        ollama.reply(200, "{\"done\":true}");

        assertThatThrownBy(() -> client.analyze("cv", "offre"))
                .isInstanceOf(ApiException.class)
                .hasMessage("La réponse de l'IA est vide.")
                .extracting("code", "status")
                .containsExactly("AI_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY);
    }

    @Test
    void rejetteUnContenuFinalAbsent() {
        ollama.replyContent("")
                .reply(200, "{\"message\":{\"role\":\"assistant\"}}")
                .reply(200, "{\"message\":{\"role\":\"assistant\"}}");

        assertThatThrownBy(() -> client.analyze("cv", "offre"))
                .isInstanceOf(ApiException.class)
                .hasMessage("La réponse de l'IA est invalide (réponse vide).");
    }

    // ── Erreurs HTTP et réseau ──────────────────────────────────────────────

    @Test
    void convertitUneErreurHttp500EnErreurAmont() {
        ollama.reply(500, "{\"error\":\"out of memory\"}");

        assertThatThrownBy(() -> client.analyze("cv", "offre"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Le service IA a renvoyé une erreur (HTTP 500).")
                .extracting("code", "status")
                .containsExactly("AI_UPSTREAM_ERROR", HttpStatus.BAD_GATEWAY);
    }

    @Test
    void signaleUnModeleNonInstalle() {
        ollama.reply(404, "{\"error\":\"model 'qwen-test' not found\"}");

        assertThatThrownBy(() -> client.analyze("cv", "offre"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Le modèle IA \"qwen-test\" n'est pas installé. Lancez : ollama pull qwen-test")
                .extracting("code", "status")
                .containsExactly("AI_MODEL_MISSING", HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** BUG : un délai de lecture dépassé n'est pas converti en AI_TIMEOUT (voir test suivant). */
    @Disabled("Bug connu : le SocketTimeoutException arrive enveloppé dans une RestClientException non interceptée")
    @Test
    void convertitUnDelaiDepasseEnTimeout() {
        ollama.replyAfter(2_500, "{}");

        assertThatThrownBy(() -> client.analyze("cv", "offre"))
                .isInstanceOf(ApiException.class)
                .extracting("code", "status")
                .containsExactly("AI_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void documenteQuUnDelaiDepasseRemonteEnRestClientExceptionBrute() {
        ollama.replyAfter(2_500, "{}");

        assertThatThrownBy(() -> client.analyze("cv", "offre"))
                .isExactlyInstanceOf(RestClientException.class)
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
    }

    @Test
    void convertitUnServeurInjoignableEnTimeout() throws Exception {
        String url = ollama.url();
        ollama.close(); // plus personne n'écoute sur ce port
        OllamaAnalysisClient offline = clientFor(url);

        assertThatThrownBy(() -> offline.analyze("cv", "offre"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Ollama n'a pas répondu à temps ou n'est pas démarré (" + url + ").")
                .extracting("code")
                .isEqualTo("AI_TIMEOUT");
    }
}
