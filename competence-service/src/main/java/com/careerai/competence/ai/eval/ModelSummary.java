package com.careerai.competence.ai.eval;

import java.util.Comparator;
import java.util.List;

/** Synthèse chiffrée d'un modèle sur tout le jeu de test. */
public record ModelSummary(String modele, int total, int erreurs, int conformes, double tauxConformite,
                           Double scoreMoyen, double tauxVerifiee,
                           long latenceMoyenneMs, long latenceP50Ms, long latenceP95Ms,
                           List<CaseResult> details) {

    public static ModelSummary of(String model, List<CaseResult> results) {
        int total = results.size();
        List<CaseResult> ok = results.stream().filter(r -> r.erreur() == null).toList();
        int conformes = (int) ok.stream().filter(CaseResult::conforme).count();
        Double score = ok.stream().map(CaseResult::score).filter(s -> s != null)
                .mapToDouble(Double::doubleValue).average().stream().boxed().findFirst()
                .map(d -> Math.round(d * 100) / 100.0).orElse(null);
        long verifiees = ok.stream().filter(r -> "VERIFIEE".equals(r.statut())).count();
        List<Long> lat = ok.stream().map(CaseResult::latenceMs).sorted(Comparator.naturalOrder()).toList();
        long avg = lat.isEmpty() ? 0 : Math.round(lat.stream().mapToLong(Long::longValue).average().orElse(0));
        return new ModelSummary(model, total, total - ok.size(), conformes,
                total == 0 ? 0 : round1(100.0 * conformes / total), score,
                ok.isEmpty() ? 0 : round1(100.0 * verifiees / ok.size()),
                avg, percentile(lat, 50), percentile(lat, 95), results);
    }

    /** Percentile « nearest-rank » sur une liste triée. */
    static long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, rank - 1)));
    }

    private static double round1(double d) {
        return Math.round(d * 10) / 10.0;
    }
}
