package com.careerai.competence.ai.eval;

import java.util.List;
import java.util.stream.Collectors;

/** Met en forme les résultats en Markdown (pour le rapport) et en CSV (pour Excel). */
public final class ReportWriter {

    private ReportWriter() {}

    public static String markdown(List<ModelSummary> summaries, String judgeModel, String date) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Évaluation comparative des modèles — competence-service\n\n");
        sb.append("Date : ").append(date).append(" · Jeu de test : ")
          .append(EvaluationDataset.CASES.size()).append(" cas · Juge (identique pour tous) : ")
          .append(judgeModel).append("\n\n");
        sb.append("| Modèle | Conformité aux règles | Note du juge (/5) | Réponses « vérifiées » | Latence moy. | p50 | p95 | Erreurs |\n");
        sb.append("|---|---|---|---|---|---|---|---|\n");
        for (ModelSummary s : summaries) {
            sb.append("| %s | %.1f %% (%d/%d) | %s | %.1f %% | %d ms | %d ms | %d ms | %d |\n".formatted(
                    s.modele(), s.tauxConformite(), s.conformes(), s.total(),
                    s.scoreMoyen() == null ? "n/a" : String.valueOf(s.scoreMoyen()),
                    s.tauxVerifiee(), s.latenceMoyenneMs(), s.latenceP50Ms(), s.latenceP95Ms(), s.erreurs()));
        }
        sb.append("\nLatence = durée de l'appel conseiller seul (le juge n'est pas compté).\n");
        for (ModelSummary s : summaries) {
            sb.append("\n## Détail — ").append(s.modele()).append("\n\n");
            sb.append("| Cas | Catégorie | Statut | Note | Latence | Règles violées / erreur |\n|---|---|---|---|---|---|\n");
            for (CaseResult r : s.details()) {
                String pb = r.erreur() != null ? "ERREUR : " + r.erreur()
                        : r.violations().isEmpty() ? "—" : String.join(" ; ", r.violations());
                sb.append("| %s | %s | %s | %s | %d ms | %s |\n".formatted(r.caseId(), r.categorie(),
                        r.statut() == null ? "—" : r.statut(), r.score() == null ? "—" : r.score(),
                        r.latenceMs(), pb));
            }
        }
        return sb.toString();
    }

    public static String csv(List<ModelSummary> summaries) {
        StringBuilder sb = new StringBuilder("modele,cas,categorie,statut,note,latence_ms,conforme,violations_ou_erreur\n");
        for (ModelSummary s : summaries) {
            for (CaseResult r : s.details()) {
                String pb = r.erreur() != null ? r.erreur() : r.violations().stream().collect(Collectors.joining(" ; "));
                sb.append("%s,%s,%s,%s,%s,%d,%s,\"%s\"\n".formatted(s.modele(), r.caseId(), r.categorie(),
                        r.statut() == null ? "" : r.statut(), r.score() == null ? "" : r.score(),
                        r.latenceMs(), r.conforme(), pb.replace("\"", "'")));
            }
        }
        return sb.toString();
    }
}
