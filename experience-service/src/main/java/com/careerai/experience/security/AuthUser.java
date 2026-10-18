package com.careerai.experience.security;

/** Utilisateur authentifie, construit a partir des claims du JWT. */
public record AuthUser(Long id, String email, String role) {
}
