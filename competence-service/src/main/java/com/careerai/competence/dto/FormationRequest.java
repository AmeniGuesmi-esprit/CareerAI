package com.careerai.competence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class FormationRequest {
    @NotBlank(message = "Le diplôme est obligatoire")
    @Size(max = 255, message = "Le diplôme est trop long")
    private String diplome;

    @NotBlank(message = "L'établissement est obligatoire")
    @Size(max = 255, message = "L'établissement est trop long")
    private String etablissement;

    @Size(max = 255, message = "Le domaine est trop long")
    private String domaine;

    @NotNull(message = "La date de début est obligatoire")
    private LocalDate dateDebut;

    private LocalDate dateFin;

    private boolean enCours;

    @Size(max = 2000, message = "La description est trop longue (2000 caractères max)")
    private String description;
}
