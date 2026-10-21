package com.careerai.competence.ai.eval;

import java.util.List;

/** Résultat d'un cas pour un modèle. erreur != null = l'appel a échoué (quota, réseau...). */
public record CaseResult(String caseId, EvaluationCase.Categorie categorie, String statut, Double score,
                         long latenceMs, List<String> violations, String erreur, String reponse) {

    public boolean conforme() {
        return erreur == null && violations.isEmpty();
    }
}
