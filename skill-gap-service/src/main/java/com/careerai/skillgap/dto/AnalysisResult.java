package com.careerai.skillgap.dto;

import java.util.List;

/** Résultat de l'analyse (validé / normalisé côté serveur avant stockage). */
public record AnalysisResult(int matchScore,
                             String summary,
                             List<String> strengths,
                             List<MissingSkill> missingSkills,
                             List<String> cvTips) {

    /** priority : HIGH | MEDIUM | LOW */
    public record MissingSkill(String skill, String priority, String reason, String howToImprove) {}
}
