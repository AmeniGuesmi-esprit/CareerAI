package com.careerai.competence.dto;

import com.careerai.competence.model.CategorieCompetence;
import com.careerai.competence.model.Competence;
import com.careerai.competence.model.NiveauCompetence;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data @Builder
public class CompetenceResponse {
    private Long id;
    private String nom;
    private NiveauCompetence niveau;
    private CategorieCompetence categorie;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CompetenceResponse from(Competence c) {
        return CompetenceResponse.builder()
                .id(c.getId())
                .nom(c.getNom())
                .niveau(c.getNiveau())
                .categorie(c.getCategorie())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
