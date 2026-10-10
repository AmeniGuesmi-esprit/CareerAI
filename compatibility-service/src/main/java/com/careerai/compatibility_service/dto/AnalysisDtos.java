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
            @JsonProperty("suggestions") List<String> suggestions) { }

    public record AnalysisResponse(
            Long id, String cvFileName, String jobTitle, int score, String summary,
            List<String> strengths, List<String> missingSkills, List<String> suggestions,
            String jobDescription, LocalDateTime createdAt) { }

    public record AnalysisListItem(
            Long id, String cvFileName, String jobTitle, int score, LocalDateTime createdAt) { }

    public record RenameRequest(@NotBlank @Size(max = 150) String jobTitle) { }
}