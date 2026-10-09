package com.careerai.user.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ForgotPasswordRequest {
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Email invalide")
    private String email;
}
