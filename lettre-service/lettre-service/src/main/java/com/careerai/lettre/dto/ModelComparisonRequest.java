package com.careerai.lettre.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Requête de comparaison contrôlée de plusieurs fournisseurs IA. */
public record ModelComparisonRequest(
        @NotEmpty @Size(min = 2, max = 4) List<@NotBlank String> providers,
        @NotBlank @Size(max = 12000) String systemPrompt,
        @NotBlank @Size(max = 12000) String userPrompt) {}
