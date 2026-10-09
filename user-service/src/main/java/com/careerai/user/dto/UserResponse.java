package com.careerai.user.dto;

import com.careerai.user.model.Role;
import lombok.*;

import java.time.LocalDateTime;

/** Vue publique d'un utilisateur : ne contient jamais le mot de passe ni les tokens. */
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class UserResponse {
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private String adresse;
    private Role role;
    private boolean emailVerified;
    private boolean enabled;
    private LocalDateTime createdAt;
}
