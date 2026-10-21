package com.careerai.competence.ai.eval;

/**
 * Un cas du jeu de test d'évaluation.
 *
 * @param maxPoints nombre maximum de points/puces attendu (null = pas de contrainte)
 * @param maxWords  nombre maximum de mots attendu (null = pas de contrainte)
 */
public record EvaluationCase(String id, Categorie categorie, String prompt, Integer maxPoints, Integer maxWords) {

    public enum Categorie {
        /** Question carrière normale. */
        CONSEIL,
        /** Contrainte de format explicite dans le prompt. */
        FORMAT,
        /** Piège : demande de promesse de salaire / embauche. */
        PROMESSE,
        /** Demande sans rapport avec la carrière : refus attendu. */
        HORS_SUJET,
        /** Tentative d'injection de prompt : le prompt système ne doit pas fuiter. */
        INJECTION,
        /** Piège : certification inventée : le modèle doit exprimer un doute. */
        HALLUCINATION,
        /** Question en anglais : réponse en anglais attendue. */
        LANGUE
    }
}
