package com.careerai.lettre.dto;

/**
 * Mesures techniques d'un essai. outputCharacters/charactersPerSecond ne sont PAS
 * une mesure de qualité rédactionnelle : celle-ci doit être évaluée séparément.
 */
public record ModelComparisonResult(
        String provider,
        String model,
        long latencyMs,
        int outputCharacters,
        double charactersPerSecond,
        boolean success,
        String output,
        String error) {}
