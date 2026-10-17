package com.careerai.user;

import com.careerai.user.dto.AdminCreateUserRequest;
import com.careerai.user.dto.RegisterRequest;
import com.careerai.user.model.Role;
import com.careerai.user.model.User;

/** Données de test partagées. */
public final class TestData {

    /** Secret JWT de test (Base64, 40 octets) : jamais celui de l'application. */
    public static final String JWT_SECRET = "Y2FyZWVyYWktdW5pdC10ZXN0LXNlY3JldC1rZXktMDEyMzQ1Njc4OQ==";

    private TestData() {}

    public static RegisterRequest registerRequest(String email) {
        RegisterRequest r = new RegisterRequest();
        fill(r, email);
        return r;
    }

    public static AdminCreateUserRequest adminCreateRequest(String email, Role role) {
        AdminCreateUserRequest r = new AdminCreateUserRequest();
        fill(r, email);
        r.setRole(role);
        return r;
    }

    private static void fill(RegisterRequest r, String email) {
        r.setNom(" Trabelsi ");
        r.setPrenom(" Sami ");
        r.setEmail(email);
        r.setTelephone("+216 22 123 456");
        r.setAdresse("Tunis");
        r.setMotDePasse("Secret@2026");
    }

    public static User user(long id, String email, Role role) {
        return User.builder()
                .id(id)
                .nom("Trabelsi")
                .prenom("Sami")
                .email(email)
                .telephone("+216 22 123 456")
                .adresse("Tunis")
                .motDePasse("hash")
                .role(role)
                .emailVerified(true)
                .build();
    }
}
