package com.careerai.competence.ai;

import com.careerai.competence.dto.AdviceRequest;
import com.careerai.competence.dto.AdviceResponse;
import com.careerai.competence.dto.AdviceResponse.Metriques;
import com.careerai.competence.exception.AiException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdvisorService {

    private static final String[] CRITERES = {"pertinence", "fiabilite", "format", "prudence"};

    private final GeminiClient gemini;
    private final RateLimiter rateLimiter;

    @Value("${app.gemini.judge-enabled:true}")
    private boolean judgeEnabled;

    @Value("${app.gemini.rate-limit-per-minute:5}")
    private int ratePerMinute;

    /**
     * Cache : même prompt = pas de nouvel appel Gemini (et le rate limiter est ignoré).
     * Aucune donnée personnelle n'entre dans le prompt, donc le partage du cache entre utilisateurs est sans risque.
     * Les métriques d'une réponse en cache sont celles de sa génération d'origine.
     */
    @Cacheable(value = "advice", key = "#req.prompt().strip().toLowerCase()")
    public AdviceResponse advise(Long userId, AdviceRequest req) {
        rateLimiter.check(userId, ratePerMinute, 60_000);
        return generate(null, req.prompt(), judgeEnabled);
    }

    /**
     * Chaîne complète conseiller -> juge, sans cache ni rate limiter.
     * Sert aussi à l'évaluation comparative des modèles (modelName = modèle du conseiller ;
     * le juge reste toujours le modèle par défaut pour que la notation soit comparable).
     *
     * @param modelName modèle du conseiller, ou null pour le modèle par défaut
     */
    public AdviceResponse generate(String modelName, String rawPrompt, boolean withJudge) {
        // Garde-fou d'entrée
        String prompt = clean(rawPrompt);
        String usedModel = modelName != null ? modelName : gemini.getModel();

        // Étape 1 : le conseiller répond (prompt système + few-shot)
        long t0 = System.nanoTime();
        String answer = modelName == null
                ? gemini.generateText(PromptTemplates.ADVISOR_SYSTEM, prompt)
                : gemini.generateText(modelName, PromptTemplates.ADVISOR_SYSTEM, prompt);
        long advisorMs = ms(t0);
        answer = cut(answer, 4000);

        if (!withJudge) {
            return build(answer, "NON_VERIFIEE", null,
                    new Metriques(usedModel, advisorMs, null, advisorMs, null));
        }

        // Étape 2 : le juge relit la réponse (chaînage + sortie JSON + notes chiffrées)
        long t1 = System.nanoTime();
        try {
            String judgeInput = "<demande>%s</demande>\n<reponse>%s</reponse>".formatted(prompt, answer);
            JsonNode verdict = gemini.generateJson(PromptTemplates.JUDGE_SYSTEM, judgeInput,
                    PromptTemplates.JUDGE_SCHEMA);
            long judgeMs = ms(t1);
            Metriques m = new Metriques(usedModel, advisorMs, judgeMs, advisorMs + judgeMs, score(verdict));
            if ("OK".equals(verdict.path("verdict").asText())) {
                return build(answer, "VERIFIEE", null, m);
            }
            return build(answer, "A_VERIFIER", verdict.path("raison").asText(null), m);
        } catch (AiException e) {
            // Le juge est un bonus : s'il échoue, on garde la réponse du conseiller
            log.warn("Juge indisponible : {}", e.getMessage());
            long judgeMs = ms(t1);
            return build(answer, "NON_VERIFIEE", null,
                    new Metriques(usedModel, advisorMs, judgeMs, advisorMs + judgeMs, null));
        }
    }

    private AdviceResponse build(String answer, String statut, String warning, Metriques m) {
        log.info("Conseil généré : modèle={} statut={} conseil={}ms juge={}ms total={}ms score={}",
                m.modele(), statut, m.latenceConseilMs(), m.latenceJugeMs(), m.latenceTotaleMs(), m.scoreQualite());
        return new AdviceResponse(answer, statut, warning, m, AdviceResponse.LIMITES_IA);
    }

    /** Moyenne /5 des critères présents et valides (1..5) ; null si le juge n'a rien noté. */
    static Double score(JsonNode verdict) {
        List<Integer> notes = new ArrayList<>();
        for (String c : CRITERES) {
            int n = verdict.path(c).asInt(0);
            if (n >= 1 && n <= 5) notes.add(n);
        }
        if (notes.isEmpty()) return null;
        double avg = notes.stream().mapToInt(Integer::intValue).average().orElse(0);
        return Math.round(avg * 10) / 10.0;
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
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
