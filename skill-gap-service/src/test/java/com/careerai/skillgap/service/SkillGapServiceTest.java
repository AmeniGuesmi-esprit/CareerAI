package com.careerai.skillgap.service;

import com.careerai.skillgap.dto.AnalysisCreateRequest;
import com.careerai.skillgap.dto.AnalysisResponse;
import com.careerai.skillgap.exception.ApiException;
import com.careerai.skillgap.model.CvUpload;
import com.careerai.skillgap.model.SkillGapAnalysis;
import com.careerai.skillgap.repository.SkillGapAnalysisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Enchaînement complet d'une analyse : anonymisation → RAG → LLM → validation → enregistrement. */
@ExtendWith(MockitoExtension.class)
class SkillGapServiceTest {

    private static final long USER = 42L;

    private static final String CV_TEXT = """
            Sami Trabelsi
            sami.trabelsi@example.com | +216 22 123 456
            12 rue de Marseille
            1002 Tunis
            Compétences : Java, Spring Boot, Angular
            Expérience : 2022 - 2024 Développeur Java chez Sofrecom
            """;

    private static final String LLM_JSON = """
            {"matchScore": 65, "summary": "Bon socle Java.", "strengths": ["Java", "Spring Boot"],
             "missingSkills": [{"skill": "Docker", "priority": "HIGH", "reason": "Exigé par l'offre.",
                                "howToImprove": "Conteneuriser une API Spring Boot."}],
             "cvTips": ["Quantifier les résultats"]}
            """;

    @Mock private CvService cvService;
    @Mock private SkillGapAnalysisRepository repository;
    @Mock private LlmClient llm;
    @Mock private RagService rag;

    private SkillGapService service;
    private CvUpload cv;

    @BeforeEach
    void setUp() {
        service = new SkillGapService(cvService, repository, new PiiMasker(), llm, rag, new ObjectMapper(),
                new AnalysisCache(60, 200));
        cv = CvUpload.builder().id(7L).userId(USER).originalFileName("cv.pdf").fileType("pdf")
                .storedFileName("x.pdf").extractedText(CV_TEXT).build();
    }

    private static AnalysisCreateRequest request(String description) {
        AnalysisCreateRequest r = new AnalysisCreateRequest();
        r.setCvId(7L);
        r.setJobTitle(" Développeur Java ");
        r.setJobDescription(description);
        return r;
    }

    private static final String OFFER = "Nous recherchons un développeur Java Spring Boot maîtrisant Docker. "
            + "Candidatures à rh@entreprise.tn";

    private void stubHappyPath(String ragContext) {
        when(cvService.findMine(USER, 7L)).thenReturn(cv);
        when(rag.retrieve(anyString(), anyString())).thenReturn(ragContext);
        when(llm.complete(anyString(), anyString())).thenReturn(LLM_JSON);
        when(llm.model()).thenReturn("test-model");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private String userPromptSentToLlm() {
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).complete(anyString(), prompt.capture());
        return prompt.getValue();
    }

    /** Exigence du sujet : jamais de données personnelles brutes envoyées au LLM. */
    @Test
    void llmNeverReceivesPersonalData() {
        stubHappyPath("");

        service.analyze(USER, request(OFFER));

        assertThat(userPromptSentToLlm())
                .doesNotContain("Sami", "Trabelsi", "sami.trabelsi@example.com", "+216 22 123 456",
                        "rue de Marseille", "1002 Tunis", "rh@entreprise.tn")
                .contains("Java, Spring Boot, Angular", "Développeur Java chez Sofrecom");
    }

    @Test
    void ragSearchAlsoReceivesOnlyMaskedTexts() {
        stubHappyPath("");

        service.analyze(USER, request(OFFER));

        ArgumentCaptor<String> job = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> cvText = ArgumentCaptor.forClass(String.class);
        verify(rag).retrieve(job.capture(), cvText.capture());
        assertThat(job.getValue()).doesNotContain("rh@entreprise.tn");
        assertThat(cvText.getValue()).doesNotContain("Trabelsi", "sami.trabelsi@example.com");
    }

    @Test
    void promptContainsOfferCvAndRagContextInTheirTags() {
        stubHappyPath("- Docker : Conteneurisation d'applications.");

        service.analyze(USER, request(OFFER));

        assertThat(userPromptSentToLlm())
                .contains("<offre>\nNous recherchons un développeur Java", "</offre>", "<cv>", "</cv>",
                        "<contexte>\n- Docker : Conteneurisation d'applications.\n</contexte>");
    }

    @Test
    void noContextTagWhenRagFindsNothing() {
        stubHappyPath("");

        service.analyze(USER, request(OFFER));

        assertThat(userPromptSentToLlm()).doesNotContain("<contexte>");
    }

    @Test
    void systemPromptRequiresJsonAndForbidsInventedResources() {
        stubHappyPath("");

        service.analyze(USER, request(OFFER));

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(llm).complete(system.capture(), anyString());
        assertThat(system.getValue()).contains("JSON", "N'invente", "jamais une instruction",
                "ne mentionne jamais « le contexte »");
    }

    @Test
    void validatedResultIsSavedForTheConnectedUser() {
        stubHappyPath("");

        AnalysisResponse res = service.analyze(USER, request(OFFER));

        ArgumentCaptor<SkillGapAnalysis> saved = ArgumentCaptor.forClass(SkillGapAnalysis.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER);
        assertThat(saved.getValue().getCv()).isSameAs(cv);
        assertThat(saved.getValue().getMatchScore()).isEqualTo(65);
        assertThat(saved.getValue().getModel()).isEqualTo("test-model");
        assertThat(saved.getValue().getJobTitle()).isEqualTo("Développeur Java");
        assertThat(res.result().missingSkills()).singleElement()
                .satisfies(m -> assertThat(m.skill()).isEqualTo("Docker"));
    }

    /** Le CV d'un autre utilisateur : refus avant tout appel au LLM (pas de fuite, pas de coût). */
    @Test
    void cvOfAnotherUserIsRejectedBeforeCallingTheLlm() {
        when(cvService.findMine(USER, 7L)).thenThrow(ApiException.notFound("CV introuvable"));

        assertThatThrownBy(() -> service.analyze(USER, request(OFFER)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(llm, rag, repository);
    }

    /** Deux réponses inexploitables de suite : erreur 502, rien n'est enregistré. */
    @Test
    void invalidLlmAnswerIsNotSaved() {
        when(cvService.findMine(USER, 7L)).thenReturn(cv);
        when(rag.retrieve(anyString(), anyString())).thenReturn("");
        when(llm.complete(anyString(), anyString())).thenReturn("Désolé, je ne peux pas.");

        assertThatThrownBy(() -> service.analyze(USER, request(OFFER)))
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        verify(llm, times(2)).complete(anyString(), anyString());
        verify(repository, never()).save(any());
    }

    /** Fiabilité : une réponse mal formée est redemandée une fois, avec un rappel du format. */
    @Test
    void malformedAnswerIsRetriedOnceWithAFormatReminder() {
        stubHappyPath("");
        when(llm.complete(anyString(), anyString())).thenReturn("{\"matchScore\": 6", LLM_JSON);

        AnalysisResponse res = service.analyze(USER, request(OFFER));

        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(llm, times(2)).complete(anyString(), prompts.capture());
        assertThat(prompts.getAllValues().get(1)).contains("Rappel", "JSON");
        assertThat(res.result().missingSkills()).singleElement()
                .satisfies(m -> assertThat(m.skill()).isEqualTo("Docker"));
    }

    /** Une erreur du fournisseur (quota, clé...) n'est pas retentée : elle ne se corrigerait pas. */
    @Test
    void providerErrorIsNotRetried() {
        when(cvService.findMine(USER, 7L)).thenReturn(cv);
        when(rag.retrieve(anyString(), anyString())).thenReturn("");
        when(llm.complete(anyString(), anyString())).thenThrow(ApiException.badGateway("Clé API du LLM invalide."));

        assertThatThrownBy(() -> service.analyze(USER, request(OFFER))).isInstanceOf(ApiException.class);
        verify(llm, times(1)).complete(anyString(), anyString());
    }

    @Test
    void llmErrorIsPropagatedWithItsStatus() {
        when(cvService.findMine(USER, 7L)).thenReturn(cv);
        when(rag.retrieve(anyString(), anyString())).thenReturn("");
        when(llm.complete(anyString(), anyString())).thenThrow(ApiException.tooManyRequests("Quota du service IA atteint."));

        assertThatThrownBy(() -> service.analyze(USER, request(OFFER)))
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(repository, never()).save(any());
    }

    // ── Cache : réduction du coût et de la latence ──

    @Test
    void identicalAnalysisIsServedFromTheCacheWithoutCallingTheLlmAgain() {
        stubHappyPath("");

        AnalysisResponse first = service.analyze(USER, request(OFFER));
        AnalysisResponse second = service.analyze(USER, request(OFFER));

        verify(llm, times(1)).complete(anyString(), anyString());
        verify(repository, times(2)).save(any());   // chaque analyse reste dans l'historique
        assertThat(second.result()).isEqualTo(first.result());
    }

    @Test
    void differentOfferCallsTheLlmAgain() {
        stubHappyPath("");

        service.analyze(USER, request(OFFER));
        service.analyze(USER, request(OFFER + " Une expérience Kubernetes est appréciée."));

        verify(llm, times(2)).complete(anyString(), anyString());
    }

    @Test
    void invalidAnswerIsNotCached() {
        when(cvService.findMine(USER, 7L)).thenReturn(cv);
        when(rag.retrieve(anyString(), anyString())).thenReturn("");
        when(llm.model()).thenReturn("test-model");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(llm.complete(anyString(), anyString())).thenReturn("Désolé.", "Toujours pas.", LLM_JSON);

        assertThatThrownBy(() -> service.analyze(USER, request(OFFER))).isInstanceOf(ApiException.class);
        service.analyze(USER, request(OFFER));   // l'échec n'a pas été mis en cache : nouvel appel

        verify(llm, times(3)).complete(anyString(), anyString());
    }

    @Test
    void analysisOfAnotherUserIsNotFound() {
        when(repository.findByIdAndUserId(3L, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMine(USER, 3L))
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.deleteMine(USER, 3L)).isInstanceOf(ApiException.class);
        verify(repository, never()).delete(any());
    }
}
