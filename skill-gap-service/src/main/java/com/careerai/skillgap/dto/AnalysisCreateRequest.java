package com.careerai.skillgap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** L'id de l'utilisateur n'est PAS dans le corps : il vient du JWT. */
@Data
public class AnalysisCreateRequest {

    public static final int JOB_MIN = 50;
    public static final int JOB_MAX = 6000;

    @NotNull(message = "Le CV est obligatoire")
    private Long cvId;

    @Size(max = 200, message = "Le titre du poste est trop long (200 caractères maximum)")
    private String jobTitle;

    @NotBlank(message = "La description de l'offre est obligatoire")
    @Size(min = JOB_MIN, max = JOB_MAX,
            message = "La description de l'offre doit faire entre " + JOB_MIN + " et " + JOB_MAX + " caractères")
    private String jobDescription;
}
