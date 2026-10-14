package com.careerai.competence.dto;

import com.careerai.competence.model.Formation;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder
public class FormationResponse {
    private Long id;
    private String diplome;
    private String etablissement;
    private String domaine;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private boolean enCours;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static FormationResponse from(Formation f) {
        return FormationResponse.builder()
                .id(f.getId())
                .diplome(f.getDiplome())
                .etablissement(f.getEtablissement())
                .domaine(f.getDomaine())
                .dateDebut(f.getDateDebut())
                .dateFin(f.getDateFin())
                .enCours(f.isEnCours())
                .description(f.getDescription())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }
}
