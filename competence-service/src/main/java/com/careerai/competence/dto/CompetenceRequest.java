package com.careerai.competence.dto;

import com.careerai.competence.model.CategorieCompetence;
import com.careerai.competence.model.NiveauCompetence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompetenceRequest {
    @NotBlank(message = "Le nom de la compétence est obligatoire")
    @Size(max = 255, message = "Le nom est trop long")
    private String nom;

    @NotNull(message = "Le niveau est obligatoire (DEBUTANT, INTERMEDIAIRE, AVANCE, EXPERT)")
    private NiveauCompetence niveau;

    @NotNull(message = "La catégorie est obligatoire (TECHNIQUE, SOFT_SKILL, LANGUE, OUTIL, AUTRE)")
    private CategorieCompetence categorie;
}
