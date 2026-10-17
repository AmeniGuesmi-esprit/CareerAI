package com.careerai.experience.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PitchRequest {

    /** Texte brut decrivant les experiences professionnelles. */
    @NotBlank(message = "Le texte des experiences est obligatoire")
    @Size(min = 50, max = 5000,
            message = "Le texte des experiences doit contenir entre 50 et 5000 caracteres")
    private String experiences;
}
