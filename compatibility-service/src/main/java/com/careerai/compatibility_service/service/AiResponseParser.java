package com.careerai.compatibility_service.service;

import com.careerai.compatibility_service.dto.AnalysisDtos.AiResult;
import com.careerai.compatibility_service.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/** Extrait et VALIDE le JSON renvoyé par l'IA : on ne fait jamais confiance à la sortie brute. */
@Component
@RequiredArgsConstructor
public class AiResponseParser {

    private final ObjectMapper mapper;

    public AiResult parse(String raw) {
        if (raw == null) {
            throw invalid("réponse vide");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw invalid("aucun JSON trouvé");
        }
        try {
            AiResult result = mapper.readerFor(AiResult.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(raw.substring(start, end + 1));
            return validate(result);
        } catch (JsonProcessingException e) {
            throw invalid("JSON mal formé");
        }
    }

    private AiResult validate(AiResult r) {
        if (r == null || r.score() == null || r.score() < 0 || r.score() > 100) {
            throw invalid("score hors de l'intervalle 0-100");
        }
        if (r.summary() == null || r.summary().isBlank()) {
            throw invalid("résumé manquant");
        }
        return new AiResult(r.score(), clip(r.summary().trim(), 600),
                clean(r.strengths()), clean(r.missingSkills()), clean(r.suggestions()));
    }

    private static List<String> clean(List<String> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> clip(s.trim(), 300))
                .limit(8)
                .toList();
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE",
                "La réponse de l'IA est invalide (" + detail + ").");
    }
}