package com.careerai.competence.ai;

import com.careerai.competence.dto.AdviceRequest;
import com.careerai.competence.dto.AdviceResponse;
import com.careerai.competence.exception.AiException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdvisorService {

    private final GeminiClient gemini;
    private final RateLimiter rateLimiter;

    @Value("${app.gemini.judge-enabled:true}")
    private boolean judgeEnabled;

    @Value("${app.gemini.rate-limit-per-minute:5}")
    private int ratePerMinute;

    /**
     * Cache : même prompt = pas de nouvel appel Gemini (et le rate limiter est ignoré).
     * Aucune donnée personnelle n'entre dans le prompt, donc le partage du cache entre utilisateurs est sans risque.
     */
    @Cacheable(value = "advice", key = "#req.prompt().strip().toLowerCase()")
    public AdviceResponse advise(Long userId, AdviceRequest req) {
        rateLimiter.check(userId, ratePerMinute, 60_000);

        // Garde-fou d'entrée
        String prompt = clean(req.prompt());

        // Étape 1 : le conseiller répond (prompt système + few-shot)
        String answer = gemini.generateText(PromptTemplates.ADVISOR_SYSTEM, prompt);
        answer = cut(answer, 4000);

        if (!judgeEnabled) return new AdviceResponse(answer, "NON_VERIFIEE", null);

        // Étape 2 : le juge relit la réponse (chaînage + sortie JSON)
        try {
            String judgeInput = "<demande>%s</demande>\n<reponse>%s</reponse>".formatted(prompt, answer);
            JsonNode verdict = gemini.generateJson(PromptTemplates.JUDGE_SYSTEM, judgeInput,
                    PromptTemplates.JUDGE_SCHEMA);
            if ("OK".equals(verdict.path("verdict").asText())) {
                return new AdviceResponse(answer, "VERIFIEE", null);
            }
            return new AdviceResponse(answer, "A_VERIFIER", verdict.path("raison").asText(null));
        } catch (AiException e) {
            // Le juge est un bonus : s'il échoue, on garde la réponse du conseiller
            log.warn("Juge indisponible : {}", e.getMessage());
            return new AdviceResponse(answer, "NON_VERIFIEE", null);
        }
    }

    private static String clean(String s) {
        return s.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ")
                .replaceAll("</?(demande|reponse)>", "")
                .strip();
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
