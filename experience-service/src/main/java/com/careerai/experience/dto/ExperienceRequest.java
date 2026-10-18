package com.careerai.experience.dto;

import com.careerai.experience.model.TypeContrat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ExperienceRequest {

    @NotBlank(message = "Le poste est obligatoire")
    @Size(max = 150, message = "Le poste ne doit pas depasser 150 caracteres")
    private String poste;

    @NotBlank(message = "L'entreprise est obligatoire")
    @Size(max = 150, message = "L'entreprise ne doit pas depasser 150 caracteres")
    private String entreprise;

    @Size(max = 150, message = "Le lieu ne doit pas depasser 150 caracteres")
    private String lieu;

    @NotNull(message = "Le type de contrat est obligatoire")
    private TypeContrat typeContrat;

    @NotNull(message = "La date de debut est obligatoire")
    private LocalDate dateDebut;

    /** Obligatoire sauf si enCours = true. */
    private LocalDate dateFin;

    private boolean enCours;

    @Size(max = 2000, message = "La description ne doit pas depasser 2000 caracteres")
    private String description;
}
