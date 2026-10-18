package com.careerai.experience.dto;

import com.careerai.experience.model.Experience;
import com.careerai.experience.model.TypeContrat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder
public class ExperienceResponse {
    private Long id;
    private String poste;
    private String entreprise;
    private String lieu;
    private TypeContrat typeContrat;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private boolean enCours;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ExperienceResponse from(Experience e) {
        return ExperienceResponse.builder()
                .id(e.getId())
                .poste(e.getPoste())
                .entreprise(e.getEntreprise())
                .lieu(e.getLieu())
                .typeContrat(e.getTypeContrat())
                .dateDebut(e.getDateDebut())
                .dateFin(e.getDateFin())
                .enCours(e.isEnCours())
                .description(e.getDescription())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
