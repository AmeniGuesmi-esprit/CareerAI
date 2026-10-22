package com.careerai.competence.dto;

/**
 * statut : VERIFIEE | A_VERIFIER | NON_VERIFIEE
 * metriques : latences mesurées + score de qualité du juge (null si le juge est désactivé ou en échec)
 * limites : rappel des limites de l'IA, affiché à l'utilisateur
 */
public record AdviceResponse(String reponse, String statut, String avertissement,
                             Metriques metriques, String limites) {

    public static final String LIMITES_IA =
            "Conseil généré par une IA : il peut contenir des erreurs ou des formations inexistantes. "
          + "Vérifie les informations auprès des organismes officiels avant toute décision. "
          + "Il ne garantit ni salaire ni embauche et ne remplace pas un conseiller humain.";

    /**
     * @param modele            modèle Gemini qui a rédigé la réponse
     * @param latenceConseilMs  durée de l'appel conseiller
     * @param latenceJugeMs     durée de l'appel juge (null si pas de juge)
     * @param latenceTotaleMs   durée totale de génération (conseiller + juge)
     * @param scoreQualite      moyenne /5 des 4 critères du juge (null si pas de juge)
     */
    public record Metriques(String modele, long latenceConseilMs, Long latenceJugeMs,
                            long latenceTotaleMs, Double scoreQualite) {}

    /** Constructeur de compatibilité (sans métriques). */
    public AdviceResponse(String reponse, String statut, String avertissement) {
        this(reponse, statut, avertissement, null, LIMITES_IA);
    }
}
