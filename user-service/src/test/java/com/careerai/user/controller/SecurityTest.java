package com.careerai.user.controller;

import com.careerai.user.config.SecurityConfig;
import com.careerai.user.dto.ApiResponse;
import com.careerai.user.dto.AuthResponse;
import com.careerai.user.model.Role;
import com.careerai.user.model.User;
import com.careerai.user.repository.UserRepository;
import com.careerai.user.security.JwtService;
import com.careerai.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static com.careerai.user.TestData.JWT_SECRET;
import static com.careerai.user.TestData.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Couche web + Spring Security réelles (vrais JWT signés), service et base simulés :
 * qui peut appeler quoi, et validation des données envoyées.
 */
@WebMvcTest(controllers = {AuthController.class, UserController.class})
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {"app.jwt.secret=" + JWT_SECRET, "app.jwt.expiration=3600000"})
class SecurityTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;

    @MockBean private UserService userService;
    @MockBean private UserRepository userRepository;

    private final User sami = user(5, "sami@example.com", Role.USER);
    private final User admin = user(1, "admin@example.com", Role.ADMIN);

    private String bearer(User u) {
        when(userRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.generateToken(u);
    }

    // ── Authentification ──

    @Test
    void profileRequiresAToken() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void validTokenGivesAccessToOwnProfile() throws Exception {
        when(userService.getMe("sami@example.com")).thenReturn(ApiResponse.success("Profil récupéré", null));

        mvc.perform(get("/api/users/me").header("Authorization", bearer(sami)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(userService).getMe("sami@example.com");   // l'identité vient du jeton, pas de la requête
    }

    @Test
    void forgedTokenIsRejected() throws Exception {
        String token = bearer(sami);
        mvc.perform(get("/api/users/me").header("Authorization", token.substring(0, token.length() - 3) + "abc"))
                .andExpect(status().isUnauthorized());
    }

    /** Un compte désactivé par l'admin perd l'accès immédiatement, même avec un jeton encore valide. */
    @Test
    void disabledAccountIsRejectedEvenWithAValidToken() throws Exception {
        String token = bearer(sami);
        sami.setEnabled(false);

        mvc.perform(get("/api/users/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void deletedAccountIsRejectedEvenWithAValidToken() throws Exception {
        String token = "Bearer " + jwtService.generateToken(sami);
        when(userRepository.findByEmail("sami@example.com")).thenReturn(Optional.empty());

        mvc.perform(get("/api/users/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    // ── Autorisations (rôles) ──

    @Test
    void userCannotCallAdminEndpoints() throws Exception {
        String token = bearer(sami);

        mvc.perform(get("/api/users").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/1").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(put("/api/users/5/role").param("role", "ADMIN").header("Authorization", token))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userService);
    }

    @Test
    void adminCanListUsers() throws Exception {
        when(userService.getAllUsers()).thenReturn(ApiResponse.success("Utilisateurs récupérés", List.of()));

        mvc.perform(get("/api/users").header("Authorization", bearer(admin))).andExpect(status().isOk());
    }

    /** Le rôle est relu en base à chaque requête : un admin rétrogradé perd ses droits sans attendre l'expiration. */
    @Test
    void demotedAdminLosesRightsImmediately() throws Exception {
        String adminToken = bearer(admin);
        admin.setRole(Role.USER);

        mvc.perform(get("/api/users").header("Authorization", adminToken)).andExpect(status().isForbidden());
    }

    // ── Routes publiques et validation ──

    @Test
    void loginIsPublic() throws Exception {
        when(userService.login(any())).thenReturn(ApiResponse.success("Connexion réussie", new AuthResponse("jwt", null)));

        mvc.perform(post("/api/users/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sami@example.com\",\"motDePasse\":\"Secret@2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("jwt"));
    }

    @Test
    void registerRejectsAShortPassword() throws Exception {
        mvc.perform(post("/api/users/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("sami@example.com", "+216 22 123 456", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Le mot de passe doit contenir au moins 8 caractères"));
        verifyNoInteractions(userService);
    }

    @Test
    void registerRejectsAnInvalidEmail() throws Exception {
        mvc.perform(post("/api/users/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("pas-un-email", "+216 22 123 456", "Secret@2026")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email invalide"));
    }

    @Test
    void registerRejectsAnInvalidPhoneNumber() throws Exception {
        mvc.perform(post("/api/users/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("sami@example.com", "abc", "Secret@2026")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Numéro de téléphone invalide"));
    }

    @Test
    void malformedJsonGivesAClearError() throws Exception {
        mvc.perform(post("/api/users/auth/login").contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Corps de requête invalide"));
    }

    private static String registerJson(String email, String phone, String password) {
        return """
                {"nom":"Trabelsi","prenom":"Sami","email":"%s","telephone":"%s","adresse":"Tunis","motDePasse":"%s"}
                """.formatted(email, phone, password);
    }
}
