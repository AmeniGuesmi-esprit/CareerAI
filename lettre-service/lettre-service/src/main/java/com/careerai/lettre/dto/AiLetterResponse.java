package com.careerai.lettre.dto;

import lombok.*;

import java.util.List;

/** Résultat d'une génération ou d'une amélioration (score et suggestions : amélioration seulement). */
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class AiLetterResponse {
    private String contenu;
    /** Note de la lettre d'origine sur 100 (null si le modèle ne l'a pas fournie). */
    private Integer score;
    private List<String> suggestions;
    private String provider;
    private String model;
}
