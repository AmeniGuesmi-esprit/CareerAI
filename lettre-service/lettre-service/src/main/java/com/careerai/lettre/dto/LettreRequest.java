package com.careerai.lettre.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Formulaire de création / modification d'une lettre de motivation. */
@Data
public class LettreRequest {

    @NotBlank(message = "Le nom de l'entreprise est obligatoire")
    @Size(max = 150, message = "Le nom de l'entreprise est trop long (150 caractères max)")
    private String nomEntreprise;

    /** Optionnel : intitulé du poste visé. */
    @Size(max = 150, message = "Le poste est trop long (150 caractères max)")
    private String poste;

    @NotBlank(message = "Le CV est obligatoire")
    @Size(max = 20000, message = "Le CV est trop long (20000 caractères max)")
    private String cv;

    @NotBlank(message = "La lettre de motivation est obligatoire")
    @Size(max = 20000, message = "La lettre est trop longue (20000 caractères max)")
    private String contenu;
}
