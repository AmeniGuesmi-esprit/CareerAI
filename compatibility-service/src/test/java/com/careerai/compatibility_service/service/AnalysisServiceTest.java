package com.careerai.compatibility_service.service;

import com.careerai.compatibility_service.dto.AnalysisDtos.*;
import com.careerai.compatibility_service.exception.ApiException;
import com.careerai.compatibility_service.model.Analysis;
import com.careerai.compatibility_service.repository.AnalysisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 10, 0);
    private static final String DESCRIPTION = "Développeur Java / Spring Boot, 3 ans d'expérience, Docker apprécié.";

    @Mock private AnalysisRepository repository;
    @Mock private PdfTextExtractor pdfTextExtractor;
    @Mock private PersonalDataAnonymizer anonymizer;
    @Mock private OllamaAnalysisClient aiClient;

    private final ObjectMapper mapper = new ObjectMapper();
    private AnalysisService service;

    private final AiResult aiResult = new AiResult(72, "Bon profil.", List.of("Spring Boot"),
            List.of("Kubernetes"), List.of("Ajouter Docker"), List.of("Java", "SQL"));
    private final AiMetrics metrics = new AiMetrics("qwen-test", 3, 41_250, 2_100, 380, false);
    private final OllamaAnalysisClient.Analyzed analyzed = new OllamaAnalysisClient.Analyzed(aiResult, metrics);
    private AnalysisCache cache;

    @BeforeEach
    void setUp() {
        cache = new AnalysisCache(Duration.ofHours(1), 200, Clock.systemUTC());
        service = new AnalysisService(repository, pdfTextExtractor, anonymizer, aiClient, cache, mapper);
    }

    private static MockMultipartFile cv(String name) {
        return new MockMultipartFile("cv", name, "application/pdf", "%PDF-1.7".getBytes());
    }

    /** Simule la base : attribue un id et une date à l'entité sauvegardée. */
    private void saveAssignsId(long id) {
        when(repository.save(any(Analysis.class))).thenAnswer(inv -> {
            Analysis a = inv.getArgument(0);
            a.setId(id);
            a.setCreatedAt(NOW);
            return a;
        });
    }

    private void nominalPipeline() {
        when(pdfTextExtractor.extract(any())).thenReturn("texte du cv");
        when(anonymizer.anonymize("texte du cv")).thenReturn("cv anonymisé");
        when(anonymizer.anonymize(DESCRIPTION)).thenReturn("offre anonymisée");
        when(aiClient.analyzeWithMetrics("cv anonymisé", "offre anonymisée")).thenReturn(analyzed);
    }

    private Analysis stored(long id, long userId, String resultJson) {
        return Analysis.builder().id(id).userId(userId).cvFileName("cv.pdf").jobTitle("Dev Java")
                .jobDescription(DESCRIPTION).score(72).resultJson(resultJson).createdAt(NOW).build();
    }

    // ── create ──────────────────────────────────────────────────────────────

    @Test
    void creeUneAnalyseEtRenvoieLaReponseComplete() {
        nominalPipeline();
        saveAssignsId(42);

        AnalysisResponse response = service.create(7L, cv("mon-cv.pdf"), "Dev Java", DESCRIPTION);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.cvFileName()).isEqualTo("mon-cv.pdf");
        assertThat(response.jobTitle()).isEqualTo("Dev Java");
        assertThat(response.score()).isEqualTo(72);
        assertThat(response.summary()).isEqualTo("Bon profil.");
        assertThat(response.strengths()).containsExactly("Spring Boot");
        assertThat(response.missingSkills()).containsExactly("Kubernetes");
        assertThat(response.suggestions()).containsExactly("Ajouter Docker");
        assertThat(response.verifiedSkills()).containsExactly("Java", "SQL");
        assertThat(response.jobDescription()).isEqualTo(DESCRIPTION);
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    void sauvegardeLEntiteAvecLesChampsNettoyesEtLeResultatEnJson() throws Exception {
        when(pdfTextExtractor.extract(any())).thenReturn("texte du cv");
        when(anonymizer.anonymize(anyString())).thenReturn("anonyme");
        when(aiClient.analyzeWithMetrics(anyString(), anyString())).thenReturn(analyzed);
        saveAssignsId(42);
        ArgumentCaptor<Analysis> captor = ArgumentCaptor.forClass(Analysis.class);

        service.create(7L, cv("cv.pdf"), "  Dev Java  ", "  " + DESCRIPTION + "  ");

        verify(repository).save(captor.capture());
        Analysis saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getJobTitle()).isEqualTo("Dev Java");
        assertThat(saved.getJobDescription()).isEqualTo(DESCRIPTION);
        assertThat(saved.getScore()).isEqualTo(72);
        assertThat(mapper.readValue(saved.getResultJson(), AiResult.class)).isEqualTo(aiResult);
    }

    @Test
    void envoieALIaUniquementLesTextesAnonymises() {
        when(pdfTextExtractor.extract(any())).thenReturn("texte du cv");
        when(anonymizer.anonymize(anyString())).thenReturn("anonyme");
        when(aiClient.analyzeWithMetrics(anyString(), anyString())).thenReturn(analyzed);
        saveAssignsId(1);

        service.create(1L, cv("cv.pdf"), "Dev", "  " + DESCRIPTION);

        verify(anonymizer).anonymize("texte du cv");
        // la description est anonymisée telle que reçue (non trimée)
        verify(anonymizer).anonymize("  " + DESCRIPTION);
        verify(aiClient).analyzeWithMetrics("anonyme", "anonyme");
    }

    @Test
    void remplaceUnNomDeFichierVideParCvPdf() {
        nominalPipeline();
        saveAssignsId(1);

        assertThat(service.create(1L, cv(""), "Dev", DESCRIPTION).cvFileName()).isEqualTo("cv.pdf");
    }

    @Test
    void neutraliseLesSeparateursDeCheminDansLeNomDeFichier() {
        nominalPipeline();
        saveAssignsId(1);

        assertThat(service.create(1L, cv("..\\../etc//cv.pdf"), "Dev", DESCRIPTION).cvFileName())
                .isEqualTo(".._.._etc_cv.pdf");
    }

    @Test
    void tronqueUnNomDeFichierA255Caracteres() {
        nominalPipeline();
        saveAssignsId(1);

        String name = "a".repeat(300) + ".pdf";
        assertThat(service.create(1L, cv(name), "Dev", DESCRIPTION).cvFileName())
                .hasSize(255).isEqualTo("a".repeat(255));
    }

    @Test
    void propageLErreurDuCvVideSansAppelerLIaNiLaBase() {
        MockMultipartFile empty = new MockMultipartFile("cv", "cv.pdf", "application/pdf", new byte[0]);
        when(pdfTextExtractor.extract(empty)).thenThrow(
                new ApiException(HttpStatus.BAD_REQUEST, "CV_MISSING", "Le fichier CV est obligatoire."));

        assertThatThrownBy(() -> service.create(1L, empty, "Dev", DESCRIPTION))
                .isInstanceOf(ApiException.class)
                .extracting("code", "status")
                .containsExactly("CV_MISSING", HttpStatus.BAD_REQUEST);
        verifyNoInteractions(aiClient, repository);
    }

    @Test
    void propageLErreurIaSansSauvegarder() {
        when(pdfTextExtractor.extract(any())).thenReturn("texte du cv");
        when(anonymizer.anonymize(anyString())).thenReturn("anonyme");
        when(aiClient.analyzeWithMetrics(anyString(), anyString())).thenThrow(
                new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "Ollama n'a pas répondu."));

        assertThatThrownBy(() -> service.create(1L, cv("cv.pdf"), "Dev", DESCRIPTION))
                .isInstanceOf(ApiException.class)
                .extracting("code", "status")
                .containsExactly("AI_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT);
        verifyNoInteractions(repository);
    }

    // ── mesures et cache ────────────────────────────────────────────────────

    @Test
    void enregistreEtRenvoieLesMesuresDeLAppelIa() {
        nominalPipeline();
        saveAssignsId(42);
        ArgumentCaptor<Analysis> captor = ArgumentCaptor.forClass(Analysis.class);

        AnalysisResponse response = service.create(7L, cv("cv.pdf"), "Dev Java", DESCRIPTION);

        verify(repository).save(captor.capture());
        Analysis saved = captor.getValue();
        assertThat(saved.getModel()).isEqualTo("qwen-test");
        assertThat(saved.getLlmCalls()).isEqualTo(3);
        assertThat(saved.getLatencyMs()).isEqualTo(41_250L);
        assertThat(saved.getPromptTokens()).isEqualTo(2_100);
        assertThat(saved.getCompletionTokens()).isEqualTo(380);
        assertThat(saved.getFromCache()).isFalse();
        assertThat(response.metrics()).isEqualTo(metrics);
    }

    @Test
    void resertUneAnalyseIdentiqueDepuisLeCacheSansRappelerLIa() {
        nominalPipeline();
        when(aiClient.model()).thenReturn("qwen-test");
        saveAssignsId(1);

        AnalysisResponse first = service.create(1L, cv("cv.pdf"), "Dev", DESCRIPTION);
        AnalysisResponse second = service.create(1L, cv("cv.pdf"), "Dev", DESCRIPTION);

        verify(aiClient, times(1)).analyzeWithMetrics(anyString(), anyString());
        verify(repository, times(2)).save(any(Analysis.class)); // chaque demande reste dans l'historique
        assertThat(second.score()).isEqualTo(first.score());
        assertThat(second.verifiedSkills()).isEqualTo(first.verifiedSkills());
        assertThat(second.metrics().cached()).isTrue();
        assertThat(second.metrics().llmCalls()).isZero();
        assertThat(second.metrics().promptTokens()).isZero();
        assertThat(second.metrics().model()).isEqualTo("qwen-test");
        assertThat(cache.hits()).isEqualTo(1);
    }

    @Test
    void rappelleLIaQuandLOffreChange() {
        when(pdfTextExtractor.extract(any())).thenReturn("texte du cv");
        when(anonymizer.anonymize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(aiClient.analyzeWithMetrics(anyString(), anyString())).thenReturn(analyzed);
        saveAssignsId(1);

        service.create(1L, cv("cv.pdf"), "Dev", DESCRIPTION);
        service.create(1L, cv("cv.pdf"), "Dev", DESCRIPTION + " Kafka souhaité.");

        verify(aiClient, times(2)).analyzeWithMetrics(anyString(), anyString());
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void rappelleLIaQuandLeModeleChange() {
        nominalPipeline();
        when(aiClient.model()).thenReturn("qwen2.5:7b", "qwen2.5:3b");
        saveAssignsId(1);

        service.create(1L, cv("cv.pdf"), "Dev", DESCRIPTION);
        service.create(1L, cv("cv.pdf"), "Dev", DESCRIPTION);

        verify(aiClient, times(2)).analyzeWithMetrics(anyString(), anyString());
    }

    @Test
    void neMetPasEnCacheUneErreurIa() {
        when(pdfTextExtractor.extract(any())).thenReturn("texte du cv");
        when(anonymizer.anonymize(anyString())).thenReturn("anonyme");
        when(aiClient.analyzeWithMetrics(anyString(), anyString()))
                .thenThrow(new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "Ollama n'a pas répondu."))
                .thenReturn(analyzed);
        saveAssignsId(1);

        assertThatThrownBy(() -> service.create(1L, cv("cv.pdf"), "Dev", DESCRIPTION))
                .isInstanceOf(ApiException.class);
        AnalysisResponse retry = service.create(1L, cv("cv.pdf"), "Dev", DESCRIPTION);

        assertThat(retry.metrics().cached()).isFalse();
        verify(aiClient, times(2)).analyzeWithMetrics(anyString(), anyString());
    }

    @Test
    void renvoieDesMesuresNullesPourUneAnalyseAnterieureALaMesure() throws Exception {
        when(repository.findByIdAndUserId(3L, 7L))
                .thenReturn(Optional.of(stored(3, 7, mapper.writeValueAsString(aiResult))));

        assertThat(service.get(7L, 3L).metrics()).isNull();
    }

    // ── list / get ──────────────────────────────────────────────────────────

    @Test
    void listeLesAnalysesDeLUtilisateurAuFormatResume() throws Exception {
        Pageable pageable = PageRequest.of(0, 10);
        when(repository.findByUserId(7L, pageable)).thenReturn(
                new PageImpl<>(List.of(stored(3, 7, mapper.writeValueAsString(aiResult))), pageable, 1));

        List<AnalysisListItem> items = service.list(7L, pageable).getContent();

        assertThat(items).containsExactly(new AnalysisListItem(3L, "cv.pdf", "Dev Java", 72, NOW));
    }

    @Test
    void renvoieLeDetailDUneAnalyse() throws Exception {
        when(repository.findByIdAndUserId(3L, 7L))
                .thenReturn(Optional.of(stored(3, 7, mapper.writeValueAsString(aiResult))));

        AnalysisResponse response = service.get(7L, 3L);

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.summary()).isEqualTo("Bon profil.");
        assertThat(response.verifiedSkills()).containsExactly("Java", "SQL");
    }

    @Test
    void renvoieUneListeVidePourLesAnciennesAnalysesSansCompetencesVerifiees() {
        String oldJson = "{\"score\":60,\"resume\":\"Ancien.\",\"points_forts\":[],"
                + "\"competences_manquantes\":[],\"suggestions\":[]}";
        when(repository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(stored(3, 7, oldJson)));

        assertThat(service.get(7L, 3L).verifiedSkills()).isEmpty();
    }

    @Test
    void renvoie404PourUneAnalyseInexistanteOuDUnAutreUtilisateur() {
        when(repository.findByIdAndUserId(3L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L, 3L))
                .isInstanceOf(ApiException.class)
                .hasMessage("Analyse introuvable : 3")
                .extracting("code", "status")
                .containsExactly("ANALYSIS_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    @Test
    void signaleUnResultatStockeIllisible() {
        when(repository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(stored(3, 7, "pas du json")));

        assertThatThrownBy(() -> service.get(7L, 3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Résultat stocké illisible pour l'analyse 3");
    }

    // ── rename / delete ─────────────────────────────────────────────────────

    @Test
    void renommeEnSupprimantLesEspaces() throws Exception {
        Analysis entity = stored(3, 7, mapper.writeValueAsString(aiResult));
        when(repository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(entity));

        AnalysisResponse response = service.rename(7L, 3L, "  Lead Dev  ");

        assertThat(response.jobTitle()).isEqualTo("Lead Dev");
        assertThat(entity.getJobTitle()).isEqualTo("Lead Dev");
    }

    @Test
    void supprimeLAnalyseTrouvee() throws Exception {
        Analysis entity = stored(3, 7, mapper.writeValueAsString(aiResult));
        when(repository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(entity));

        service.delete(7L, 3L);

        verify(repository).delete(entity);
    }

    @Test
    void neSupprimeRienSiLAnalyseEstIntrouvable() {
        when(repository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(7L, 3L))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("ANALYSIS_NOT_FOUND");
        verify(repository, never()).delete(any());
    }
}
