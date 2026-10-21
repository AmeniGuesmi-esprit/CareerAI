package com.careerai.lettre.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Demande de génération d'une lettre à partir du CV et de l'offre. Rien n'est enregistré. */
@Data
public class GenerateLetterRequest {

    @NotBlank(message = "Le nom de l'entreprise est obligatoire")
    @Size(max = 150, message = "Le nom de l'entreprise est trop long (150 caractères max)")
    private String nomEntreprise;

    @Size(max = 150, message = "Le poste est trop long (150 caractères max)")
    private String poste;

    @NotBlank(message = "L'offre d'emploi est obligatoire")
    @Size(max = 12000, message = "L'offre est trop longue (12000 caractères max)")
    private String offre;

    @NotBlank(message = "Le CV est obligatoire")
    @Size(max = 20000, message = "Le CV est trop long (20000 caractères max)")
    private String cv;

    @Pattern(regexp = "professionnel|dynamique|chaleureux|concis", message = "Ton non reconnu")
    private String ton = "professionnel";

    @Pattern(regexp = "fr|en", message = "Langue non reconnue (fr ou en)")
    private String langue = "fr";

    /** Optionnel : mistral ou cohere. Vide = fournisseur par défaut. */
    @Size(max = 30)
    private String provider;
}
