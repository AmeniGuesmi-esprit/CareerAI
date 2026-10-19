package com.careerai.skillgap.controller;

import com.careerai.skillgap.config.SecurityConfig;
import com.careerai.skillgap.exception.ApiException;
import com.careerai.skillgap.service.CvService;
import com.careerai.skillgap.service.SkillGapService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Couche web + Spring Security réelles avec de vrais JWT (signés comme par user-service), services simulés.
 * L'id de l'utilisateur vient toujours du jeton, jamais de la requête.
 */
@WebMvcTest(SkillGapController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=" + SecurityTest.SECRET)
class SecurityTest {

    static final String SECRET = "Y2FyZWVyYWktdW5pdC10ZXN0LXNlY3JldC1rZXktMDEyMzQ1Njc4OQ==";

    @Autowired private MockMvc mvc;
    @MockBean private CvService cvService;
    @MockBean private SkillGapService skillGapService;

    private static String token(String secret, Map<String, Object> claims, long validityMs) {
        return "Bearer " + Jwts.builder()
                .setClaims(claims)
                .setSubject("sami@example.com")
                .setExpiration(new Date(System.currentTimeMillis() + validityMs))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret)), SignatureAlgorithm.HS256)
                .compact();
    }

    private static String validToken() {
        return token(SECRET, Map.of("userId", 42, "role", "USER"), 60_000);
    }

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        mvc.perform(get("/api/skill-gap/cvs")).andExpect(status().isUnauthorized());
        verifyNoInteractions(cvService);
    }

    @Test
    void userIdComesFromTheToken() throws Exception {
        when(cvService.listMine(42L)).thenReturn(List.of());

        mvc.perform(get("/api/skill-gap/cvs").header("Authorization", validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(cvService).listMine(42L);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() throws Exception {
        String other = Base64.getEncoder().encodeToString("un-autre-secret-de-test-de-32-octets!!".getBytes());

        mvc.perform(get("/api/skill-gap/cvs").header("Authorization", token(other, Map.of("userId", 42), 60_000)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        mvc.perform(get("/api/skill-gap/cvs").header("Authorization", token(SECRET, Map.of("userId", 42), -1_000)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutUserIdIsRejected() throws Exception {
        mvc.perform(get("/api/skill-gap/cvs").header("Authorization", token(SECRET, Map.of("role", "USER"), 60_000)))
                .andExpect(status().isUnauthorized());
    }

    /** Un userId glissé dans le corps de la requête est ignoré : impossible d'agir au nom d'un autre. */
    @Test
    void userIdInTheBodyIsIgnored() throws Exception {
        mvc.perform(post("/api/skill-gap/analyses").header("Authorization", validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisJson("Développeur Java Spring Boot, Docker et Kubernetes exigés. ".repeat(2))
                                .replace("{", "{\"userId\": 99, ")))
                .andExpect(status().isCreated());
        verify(skillGapService).analyze(eq(42L), any());
    }

    @Test
    void tooShortOfferIsRejectedWithAClearMessage() throws Exception {
        mvc.perform(post("/api/skill-gap/analyses").header("Authorization", validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisJson("Dev Java")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La description de l'offre doit faire entre 50 et 6000 caractères"));
        verifyNoInteractions(skillGapService);
    }

    /** Les erreurs du service IA arrivent au frontend avec leur code HTTP et un message lisible. */
    @Test
    void aiServiceErrorReachesTheClientWithItsStatus() throws Exception {
        when(skillGapService.analyze(eq(42L), any()))
                .thenThrow(ApiException.unavailable("Service d'analyse indisponible : clé API du LLM absente (LLM_API_KEY)."));

        mvc.perform(post("/api/skill-gap/analyses").header("Authorization", validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisJson("Développeur Java Spring Boot, Docker et Kubernetes exigés. ".repeat(2))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Service d'analyse indisponible : clé API du LLM absente (LLM_API_KEY)."));
    }

    private static String analysisJson(String description) {
        return "{\"cvId\": 7, \"jobTitle\": \"Développeur Java\", \"jobDescription\": \"" + description + "\"}";
    }
}
