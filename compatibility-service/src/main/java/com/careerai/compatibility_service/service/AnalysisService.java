package com.careerai.compatibility_service.service;

import com.careerai.compatibility_service.dto.AnalysisDtos.*;
import com.careerai.compatibility_service.exception.ApiException;
import com.careerai.compatibility_service.model.Analysis;
import com.careerai.compatibility_service.repository.AnalysisRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private final AnalysisRepository repository;
    private final PdfTextExtractor pdfTextExtractor;
    private final PersonalDataAnonymizer anonymizer;
    private final OllamaAnalysisClient aiClient;
    private final AnalysisCache cache;
    private final ObjectMapper mapper;

    /** Volontairement SANS @Transactional : on ne garde pas une connexion SQL ouverte pendant l'appel réseau à l'IA. */
    public AnalysisResponse create(Long userId, MultipartFile cv, String jobTitle, String jobDescription) {
        String cvText = anonymizer.anonymize(pdfTextExtractor.extract(cv));
        String jobText = anonymizer.anonymize(jobDescription);
        OllamaAnalysisClient.Analyzed analyzed = analyzeOrReuse(cvText, jobText);
        AiResult result = analyzed.result();
        AiMetrics metrics = analyzed.metrics();

        Analysis saved = repository.save(Analysis.builder()
                .userId(userId)
                .cvFileName(safeFileName(cv.getOriginalFilename()))
                .jobTitle(jobTitle.trim())
                .jobDescription(jobDescription.trim())
                .score(result.score())
                .resultJson(toJson(result))
                .model(metrics.model())
                .llmCalls(metrics.llmCalls())
                .latencyMs(metrics.latencyMs())
                .promptTokens(metrics.promptTokens())
                .completionTokens(metrics.completionTokens())
                .fromCache(metrics.cached())
                .build());
        log.info("Analyse {} créée pour l'utilisateur {} (score {}, {} ms{})", saved.getId(), userId,
                saved.getScore(), metrics.latencyMs(), metrics.cached() ? ", depuis le cache" : "");
        return toResponse(saved);
    }

    /** Resservit une analyse identique depuis le cache, sinon appelle l'agent et met le résultat valide en cache. */
    private OllamaAnalysisClient.Analyzed analyzeOrReuse(String cvText, String jobText) {
        long start = System.nanoTime();
        String key = AnalysisCache.key(aiClient.model(), aiClient.promptFingerprint(), cvText, jobText);
        Optional<AnalysisCache.Entry> hit = cache.get(key);
        if (hit.isPresent()) {
            long lookupMs = (System.nanoTime() - start) / 1_000_000;
            return new OllamaAnalysisClient.Analyzed(hit.get().result(), hit.get().metrics().asCached(lookupMs));
        }
        OllamaAnalysisClient.Analyzed analyzed = aiClient.analyzeWithMetrics(cvText, jobText);
        cache.put(key, analyzed.result(), analyzed.metrics());
        return analyzed;
    }

    @Transactional(readOnly = true)
    public Page<AnalysisListItem> list(Long userId, Pageable pageable) {
        return repository.findByUserId(userId, pageable)
                .map(a -> new AnalysisListItem(a.getId(), a.getCvFileName(), a.getJobTitle(),
                        a.getScore(), a.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public AnalysisResponse get(Long userId, Long id) {
        return toResponse(find(userId, id));
    }

    @Transactional
    public AnalysisResponse rename(Long userId, Long id, String jobTitle) {
        Analysis analysis = find(userId, id);
        analysis.setJobTitle(jobTitle.trim()); // enregistré automatiquement à la fin de la transaction
        return toResponse(analysis);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(find(userId, id));
    }

    /** Une analyse d'un autre utilisateur renvoie aussi 404 : on ne révèle pas son existence. */
    private Analysis find(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", "Analyse introuvable : " + id));
    }

    private String safeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "cv.pdf";
        }
        String clean = name.replaceAll("[\\\\/]+", "_");
        return clean.length() > 255 ? clean.substring(0, 255) : clean;
    }

    private String toJson(AiResult result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation du résultat impossible", e);
        }
    }

    private static AiMetrics metricsOf(Analysis a) {
        if (a.getLatencyMs() == null) return null; // analyse antérieure à la mesure
        return new AiMetrics(a.getModel(), nz(a.getLlmCalls()), a.getLatencyMs(),
                nz(a.getPromptTokens()), nz(a.getCompletionTokens()), Boolean.TRUE.equals(a.getFromCache()));
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private AnalysisResponse toResponse(Analysis a) {
        try {
            AiResult r = mapper.readValue(a.getResultJson(), AiResult.class);
            return new AnalysisResponse(a.getId(), a.getCvFileName(), a.getJobTitle(), a.getScore(),
                    r.summary(), r.strengths(), r.missingSkills(), r.suggestions(),
                    r.verifiedSkills() == null ? List.of() : r.verifiedSkills(), // anciennes analyses
                    a.getJobDescription(), a.getCreatedAt(), metricsOf(a));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Résultat stocké illisible pour l'analyse " + a.getId(), e);
        }
    }
}