package com.careerai.lettre.security;

import com.careerai.lettre.config.SecurityConfig;
import com.careerai.lettre.controller.LettreAiController;
import com.careerai.lettre.controller.LettreController;
import com.careerai.lettre.dto.ApiResponse;
import com.careerai.lettre.exception.ApiException;
import com.careerai.lettre.service.LettreAiService;
import com.careerai.lettre.service.LettreService;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sécurité JWT et contrat de l'API, sans MySQL ni Eureka : seule la couche web est chargée
 * (filtre JWT réel, règles de sécurité réelles), les services sont simulés.
 */
@WebMvcTest(controllers = {LettreController.class, LettreAiController.class})
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = "app.jwt.secret=" + SecurityTest.SECRET)
class SecurityTest {

    static final String SECRET = "bGV0dHJlLXRlc3RzLXNlY3JldC1rZXktMzJieXRlcyE=";
    private static final String OTHER_SECRET = "YW5vdGhlci1zZWNyZXQta2V5LWZvci1mb3JnZXJ5ISE=";

    @Autowired
    MockMvc mvc;

    @MockBean
    LettreService lettreService;

    @MockBean
    LettreAiService aiService;

    // ───────────────────────── Fabrication de jetons ─────────────────────────

    private static SecretKey key(String base64) {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64));
    }

    private static String token(String subject, String role, long expiresInMs, String secret) {
        long now = System.currentTimeMillis();
        JwtBuilder b = Jwts.builder()
                .subject(subject)
                .issuedAt(new Date(now - 120_000))
                .expiration(new Date(now + expiresInMs))
                .signWith(key(secret));
        if (role != null) b.claim("role", role);
        return b.compact();
    }

    private static String bearer(String subject, String role) {
        return "Bearer " + token(subject, role, 3_600_000, SECRET);
    }

    // ───────────────────────── Authentification ─────────────────────────

    @Test
    void requestWithoutTokenIsRejectedWith401() throws Exception {
        mvc.perform(get("/api/lettres")).andExpect(status().isUnauthorized());
        verify(lettreService, never()).getMine(any());
    }

    @Test
    void garbageTokenIsRejectedWith401() throws Exception {
        mvc.perform(get("/api/lettres").header("Authorization", "Bearer n'importe.quoi.ici"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejectedWith401() throws Exception {
        String expired = "Bearer " + token("alice@example.com", "USER", -60_000, SECRET);
        mvc.perform(get("/api/lettres").header("Authorization", expired)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejectedWith401() throws Exception {
        String forged = "Bearer " + token("alice@example.com", "ADMIN", 3_600_000, OTHER_SECRET);
        mvc.perform(get("/api/lettres").header("Authorization", forged)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutRoleIsRejectedWith401() throws Exception {
        String noRole = "Bearer " + token("alice@example.com", null, 3_600_000, SECRET);
        mvc.perform(get("/api/lettres").header("Authorization", noRole)).andExpect(status().isUnauthorized());
    }

    @Test
    void aValidTokenGivesAccess() throws Exception {
        when(lettreService.getMine("alice@example.com")).thenReturn(ApiResponse.success("Lettres récupérées", List.of()));

        mvc.perform(get("/api/lettres").header("Authorization", bearer("alice@example.com", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ───────────────────────── Isolation entre utilisateurs ─────────────────────────

    @Test
    void theUserIdentityComesFromTheTokenOnly() throws Exception {
        when(lettreService.getMineById("bob@example.com", 5L)).thenReturn(ApiResponse.error("Lettre non trouvée"));

        mvc.perform(get("/api/lettres/5").header("Authorization", bearer("bob@example.com", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Lettre non trouvée"));

        verify(lettreService).getMineById("bob@example.com", 5L);
        verify(lettreService, never()).getMineById(eq("alice@example.com"), any());
    }

    // ───────────────────────── Rôles ─────────────────────────

    @Test
    void aSimpleUserCannotUseTheAdminEndpoints() throws Exception {
        mvc.perform(get("/api/lettres/admin/all").header("Authorization", bearer("alice@example.com", "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/lettres/admin/3").header("Authorization", bearer("alice@example.com", "USER")))
                .andExpect(status().isForbidden());

        verify(lettreService, never()).getAll();
        verify(lettreService, never()).deleteById(any());
    }

    @Test
    void anAdminCanListEveryLetter() throws Exception {
        when(lettreService.getAll()).thenReturn(ApiResponse.success("Lettres récupérées", List.of()));

        mvc.perform(get("/api/lettres/admin/all").header("Authorization", bearer("admin@example.com", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ───────────────────────── Validation et erreurs de l'API ─────────────────────────

    @Test
    void aLetterWithoutCompanyNameIsRefusedWith400AndAFrenchMessage() throws Exception {
        String body = "{\"nomEntreprise\":\"\",\"cv\":\"mon cv\",\"contenu\":\"ma lettre\"}";

        mvc.perform(post("/api/lettres")
                        .header("Authorization", bearer("alice@example.com", "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Le nom de l'entreprise est obligatoire"));
    }

    @Test
    void aMalformedBodyIsRefusedWith400() throws Exception {
        mvc.perform(post("/api/lettres")
                        .header("Authorization", bearer("alice@example.com", "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Corps de requête invalide"));
    }

    @Test
    void anUnknownToneIsRefusedByTheAiEndpoint() throws Exception {
        String body = "{\"nomEntreprise\":\"Acme\",\"offre\":\"Une offre\",\"cv\":\"Un CV\",\"ton\":\"agressif\"}";

        mvc.perform(post("/api/lettres/ai/generate")
                        .header("Authorization", bearer("alice@example.com", "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ton non reconnu"));
    }

    @Test
    void anAiTimeoutReachesTheClientAs504WithAClearMessage() throws Exception {
        when(aiService.generate(eq("alice@example.com"), any()))
                .thenThrow(ApiException.gatewayTimeout("Le fournisseur « mistral » met trop de temps à répondre."));
        String body = "{\"nomEntreprise\":\"Acme\",\"offre\":\"Une offre\",\"cv\":\"Un CV\"}";

        mvc.perform(post("/api/lettres/ai/generate")
                        .header("Authorization", bearer("alice@example.com", "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Le fournisseur « mistral » met trop de temps à répondre."));
    }

    @Test
    void theAiEndpointsRequireAToken() throws Exception {
        String body = "{\"nomEntreprise\":\"Acme\",\"offre\":\"Une offre\",\"cv\":\"Un CV\"}";

        mvc.perform(post("/api/lettres/ai/generate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        verify(aiService, never()).generate(any(), any());
    }
}
