package com.careerai.competence.ai.eval;

import com.careerai.competence.ai.AdvisorService;
import com.careerai.competence.dto.AdviceResponse;
import com.careerai.competence.exception.AiException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Fait passer le même jeu de test à plusieurs modèles et calcule les métriques de comparaison :
 * conformité aux règles, note du juge, taux « vérifiée », latence moyenne / p50 / p95.
 * Ce n'est pas un bean Spring : il est lancé à la demande (voir ModelBenchmarkTest).
 */
public class ModelEvaluator {

    private final AdvisorService advisor;
    private final long pauseMs;
    private final int maxRetries;

    /** @param pauseMs pause entre deux cas, pour rester sous le quota gratuit de Gemini */
    public ModelEvaluator(AdvisorService advisor, long pauseMs) {
        this(advisor, pauseMs, 2);
    }

    public ModelEvaluator(AdvisorService advisor, long pauseMs, int maxRetries) {
        this.advisor = advisor;
        this.pauseMs = pauseMs;
        this.maxRetries = maxRetries;
    }

    public List<ModelSummary> compare(List<String> models, List<EvaluationCase> cases) {
        List<ModelSummary> out = new ArrayList<>();
        for (String model : models) out.add(evaluate(model, cases));
        return out;
    }

    public ModelSummary evaluate(String model, List<EvaluationCase> cases) {
        List<CaseResult> results = new ArrayList<>();
        for (EvaluationCase c : cases) {
            results.add(runCase(model, c));
            sleep(pauseMs);
        }
        return ModelSummary.of(model, results);
    }

    CaseResult runCase(String model, EvaluationCase c) {
        for (int attempt = 0; ; attempt++) {
            try {
                AdviceResponse r = advisor.generate(model, c.prompt(), true);
                var m = r.metriques();
                return new CaseResult(c.id(), c.categorie(), r.statut(), m.scoreQualite(),
                        m.latenceConseilMs(), RuleChecker.violations(c, r.reponse()), null, r.reponse());
            } catch (AiException e) {
                boolean quota = e.getStatus() == HttpStatus.TOO_MANY_REQUESTS;
                if (quota && attempt < maxRetries) { sleep(30_000); continue; }
                return new CaseResult(c.id(), c.categorie(), null, null, 0, List.of(), e.getMessage(), null);
            }
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
