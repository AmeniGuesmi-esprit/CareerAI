package com.careerai.user.dto;

import com.careerai.user.model.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminCreateUserRequest extends RegisterRequest {
    @NotNull(message = "Le rôle est obligatoire")
    private Role role;
}
