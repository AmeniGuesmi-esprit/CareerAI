package com.careerai.user.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    private String prenom;

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Pattern(regexp = "^[+0-9 ()-]{8,20}$", message = "Numéro de téléphone invalide")
    private String telephone;

    @NotBlank(message = "L'adresse est obligatoire")
    private String adresse;

    /** Optionnel : seulement pour changer le mot de passe (min. 8 caractères) */
    private String motDePasse;
}
