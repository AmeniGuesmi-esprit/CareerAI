package com.careerai.lettre.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Demande d'amélioration d'une lettre existante. Rien n'est enregistré. */
@Data
public class ImproveLetterRequest {

    @NotBlank(message = "La lettre est obligatoire")
    @Size(max = 20000, message = "La lettre est trop longue (20000 caractères max)")
    private String contenu;

    /** Optionnel : l'offre, pour adapter la lettre. */
    @Size(max = 12000, message = "L'offre est trop longue (12000 caractères max)")
    private String offre;

    /** Optionnel : ce que l'utilisateur veut (ex. « plus court », « ton plus dynamique »). */
    @Size(max = 500, message = "La consigne est trop longue (500 caractères max)")
    private String instruction;

    @Size(max = 30)
    private String provider;
}
