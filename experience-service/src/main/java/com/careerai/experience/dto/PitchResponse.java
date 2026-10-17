package com.careerai.experience.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Presentation d'entretien ("Presentez-vous") generee en 3 versions,
 * a la premiere personne et sans fait invente (verifie par le service).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PitchResponse {

    /** Version courte (accroche) : 1 a 2 phrases, 15 a 35 mots, ~15 s a l'oral. */
    private String courte;

    /** Version standard : ton professionnel et clair, ~1 minute a l'oral (60 a 140 mots selon la richesse des donnees). */
    private String standard;

    /** Version enthousiaste : ton energique et motive, ~1 minute a l'oral (60 a 140 mots selon la richesse des donnees). */
    private String enthousiaste;
}