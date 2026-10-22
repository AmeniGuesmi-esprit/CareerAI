package com.careerai.lettre.controller;

import com.careerai.lettre.dto.*;
import com.careerai.lettre.service.LettreAiService;
import com.careerai.lettre.ai.LlmClient;
import com.careerai.lettre.dto.ModelComparisonRequest;
import com.careerai.lettre.dto.ModelComparisonResult;
import com.careerai.lettre.exception.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

/** Assistant IA : génération et amélioration de lettres. Ces endpoints n'enregistrent rien. */
@RestController
@RequestMapping("/api/lettres/ai")
@RequiredArgsConstructor
public class LettreAiController {

    private final LettreAiService aiService;
    private final LlmClient llm;

    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<List<AiProviderInfo>>> providers() {
        return ResponseEntity.ok(ApiResponse.success("Fournisseurs IA", aiService.providers()));
    }

    /** Mesures réelles depuis le démarrage (ou le dernier reset) : latence p50/p95, cache, taux de succès. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> metrics() {
        return ResponseEntity.ok(ApiResponse.success("Métriques de performance", llm.metrics()));
    }

    /** Remet les compteurs à zéro et vide le cache, avant une campagne de mesure. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/metrics/reset")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> resetMetrics() {
        llm.resetMetrics();
        return ResponseEntity.ok(ApiResponse.success("Métriques remises à zéro", llm.metrics()));
    }

    /**
     * Compare 2 à 4 fournisseurs sur exactement le même prompt (cache désactivé).
     * Réservé aux administrateurs : un prompt libre sans limite de débit consommerait le quota de tous.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/compare")
    public ResponseEntity<ApiResponse<List<ModelComparisonResult>>> compare(
            Authentication auth, @Valid @RequestBody ModelComparisonRequest request) {
        List<ModelComparisonResult> results = new ArrayList<>();
        for (String provider : request.providers()) {
            long start = System.nanoTime();
            try {
                var result = llm.chat(provider, request.systemPrompt(), request.userPrompt(), false);
                results.add(new ModelComparisonResult(result.provider(), result.model(),
                        (System.nanoTime() - start) / 1_000_000L, result.text().length(),
                        charsPerSecond(result.text().length(), System.nanoTime() - start),
                        true, result.text(), null));
            } catch (RuntimeException ex) {
                results.add(new ModelComparisonResult(provider.toLowerCase(Locale.ROOT), null,
                        (System.nanoTime() - start) / 1_000_000L, 0, 0.0,
                        false, null, "Échec du fournisseur; consultez les logs serveur."));
            }
        }
        return ResponseEntity.ok(ApiResponse.success("Comparaison terminée", results));
    }

    private static double charsPerSecond(int chars, long elapsedNanos) {
        if (elapsedNanos <= 0) return 0.0;
        return Math.round(chars * 1_000_000_000.0 / elapsedNanos * 100.0) / 100.0;
    }

    @PostMapping(value = "/extract-cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> extractCv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("CV lu avec succès", aiService.extractCv(file)));
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<AiLetterResponse>> generate(Authentication auth,
                                                                  @Valid @RequestBody GenerateLetterRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Lettre générée", aiService.generate(auth.getName(), request)));
    }

    @PostMapping("/improve")
    public ResponseEntity<ApiResponse<AiLetterResponse>> improve(Authentication auth,
                                                                 @Valid @RequestBody ImproveLetterRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Lettre améliorée", aiService.improve(auth.getName(), request)));
    }
}
