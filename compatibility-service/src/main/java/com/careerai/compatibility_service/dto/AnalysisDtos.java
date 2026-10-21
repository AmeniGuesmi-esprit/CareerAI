package com.careerai.compatibility_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class AnalysisDtos {

    private AnalysisDtos() { }

    /** Structure JSON attendue de l'IA. */
    public record AiResult(
            @JsonProperty("score") Integer score,
            @JsonProperty("resume") String summary,
            @JsonProperty("points_forts") List<String> strengths,
            @JsonProperty("competences_manquantes") List<String> missingSkills,
            @JsonProperty("suggestions") List<String> suggestions,
            @JsonProperty("competences_verifiees") List<String> verifiedSkills) {

        public AiResult withVerifiedSkills(List<String> skills) {
            return new AiResult(score, summary, strengths, missingSkills, suggestions, List.copyOf(skills));
        }
    }

    /**
     * Mesures d'une analyse : modèle, appels au LLM, latence cumulée (ms), jetons envoyés / générés,
     * et cached=true si le résultat a été resservi depuis le cache (aucun appel au LLM).
     */
    public record AiMetrics(String model, int llmCalls, long latencyMs,
                            int promptTokens, int completionTokens, boolean cached) {

        public AiMetrics asCached(long lookupMs) {
            return new AiMetrics(model, 0, lookupMs, 0, 0, true);
        }
    }

    /** metrics vaut null pour les analyses créées avant la mesure de la latence. */
    public record AnalysisResponse(
            Long id, String cvFileName, String jobTitle, int score, String summary,
            List<String> strengths, List<String> missingSkills, List<String> suggestions,
            List<String> verifiedSkills, String jobDescription, LocalDateTime createdAt,
            AiMetrics metrics) { }

    public record AnalysisListItem(
            Long id, String cvFileName, String jobTitle, int score, LocalDateTime createdAt) { }

    public record RenameRequest(@NotBlank @Size(max = 150) String jobTitle) { }
}