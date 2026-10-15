package com.careerai.competence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdviceRequest(
        @NotBlank(message = "Le prompt est obligatoire")
        @Size(min = 20, max = 1500, message = "Le prompt doit faire entre 20 et 1500 caractères")
        String prompt) {}
