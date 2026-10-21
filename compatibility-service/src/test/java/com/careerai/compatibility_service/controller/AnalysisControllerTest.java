package com.careerai.compatibility_service.controller;

import com.careerai.compatibility_service.config.SecurityConfig;
import com.careerai.compatibility_service.dto.AnalysisDtos.*;
import com.careerai.compatibility_service.exception.ApiException;
import com.careerai.compatibility_service.service.AnalysisService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Couche web + vraie chaîne Spring Security, avec de vrais JWT signés comme par user-service ; service simulé. */
@WebMvcTest(AnalysisController.class)
@Import(SecurityConfig.class)
class AnalysisControllerTest {

    /** Même valeur que app.jwt.secret dans src/test/resources/application.properties. */
    static final String SECRET = "Y2FyZWVyYWktY29tcGF0LXVuaXQtdGVzdC1zZWNyZXQtMDEyMzQ1Njc4OQ==";
    private static final String AUTH = "Authorization";
    private static final String USER_7 = token(SECRET, Map.of("userId", 7, "role", "USER"), 60_000);

    static String token(String secret, Map<String, Object> claims, long validityMs) {
        return "Bearer " + Jwts.builder()
                .setClaims(claims)
                .setSubject("candidat@example.com")
                .setExpiration(new Date(System.currentTimeMillis() + validityMs))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret)), SignatureAlgorithm.HS256)
                .compact();
    }

    private static final String URL = "/api/compatibility/analyses";
    private static final String DESCRIPTION = "Développeur Java / Spring Boot, 3 ans d'expérience, Docker apprécié.";

    @Autowired private MockMvc mvc;
    @MockBean private AnalysisService service;

    private final MockMultipartFile cv =
            new MockMultipartFile("cv", "cv.pdf", "application/pdf", "%PDF-1.7".getBytes());

    private static AnalysisResponse response(long id, String title) {
        return new AnalysisResponse(id, "cv.pdf", title, 72, "Bon profil.", List.of("Spring Boot"),
                List.of("Kubernetes"), List.of("Ajouter Docker"), List.of("Java"), DESCRIPTION,
                LocalDateTime.of(2026, 10, 15, 10, 0),
                new AiMetrics("qwen2.5:7b", 3, 41_250, 2_100, 380, false));
    }

    // ── POST ────────────────────────────────────────────────────────────────

    @Test
    void creeUneAnalyseEtRenvoie201AvecLocation() throws Exception {
        when(service.create(eq(7L), any(), eq("Dev Java"), eq(DESCRIPTION))).thenReturn(response(42, "Dev Java"));

        mvc.perform(multipart(URL).file(cv).header(AUTH, USER_7)
                        .param("jobTitle", "Dev Java").param("jobDescription", DESCRIPTION))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/compatibility/analyses/42"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.score").value(72))
                .andExpect(jsonPath("$.verifiedSkills[0]").value("Java"))
                .andExpect(jsonPath("$.metrics.model").value("qwen2.5:7b"))
                .andExpect(jsonPath("$.metrics.latencyMs").value(41_250))
                .andExpect(jsonPath("$.metrics.cached").value(false));
    }

    @Test
    void refuseUneRequeteSansJetonAvecUne401Json() throws Exception {
        mvc.perform(multipart(URL).file(cv).param("jobTitle", "Dev").param("jobDescription", DESCRIPTION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(service);
    }

    /** Régression sécurité : l'ancien en-tête X-User-Id ne permet plus de choisir l'utilisateur. */
    @Test
    void ignoreUnEnTeteXUserIdForge() throws Exception {
        when(service.get(7L, 3L)).thenReturn(response(3, "Dev Java"));

        mvc.perform(get(URL + "/3").header(AUTH, USER_7).header("X-User-Id", "99"))
                .andExpect(status().isOk());
        verify(service).get(7L, 3L);
        verify(service, never()).get(eq(99L), anyLong());
    }

    @Test
    void refuseUnEnTeteXUserIdSansJeton() throws Exception {
        mvc.perform(get(URL + "/3").header("X-User-Id", "7")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void refuseUnJetonSigneAvecUnAutreSecret() throws Exception {
        String other = Base64.getEncoder().encodeToString("un-autre-secret-de-test-de-32-octets!!".getBytes());
        mvc.perform(get(URL).header(AUTH, token(other, Map.of("userId", 7), 60_000)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void refuseUnJetonExpire() throws Exception {
        mvc.perform(get(URL).header(AUTH, token(SECRET, Map.of("userId", 7), -1_000)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refuseUnJetonSansUserId() throws Exception {
        mvc.perform(get(URL).header(AUTH, token(SECRET, Map.of("role", "USER"), 60_000)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void renvoie400SiLeCvEstAbsent() throws Exception {
        mvc.perform(multipart(URL).header(AUTH, USER_7).param("jobTitle", "Dev").param("jobDescription", DESCRIPTION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void renvoie400SiLaDescriptionEstTropCourte() throws Exception {
        mvc.perform(multipart(URL).file(cv).header(AUTH, USER_7).param("jobTitle", "Dev").param("jobDescription", "Trop court"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void renvoie400SiLIntituleEstVide() throws Exception {
        mvc.perform(multipart(URL).file(cv).header(AUTH, USER_7).param("jobTitle", "   ").param("jobDescription", DESCRIPTION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void transmetLeStatutEtLeCodeDUneApiException() throws Exception {
        when(service.create(any(), any(), any(), any())).thenThrow(
                new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "CV_NOT_PDF", "Seuls les fichiers PDF sont acceptés."));

        mvc.perform(multipart(URL).file(cv).header(AUTH, USER_7).param("jobTitle", "Dev").param("jobDescription", DESCRIPTION))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("CV_NOT_PDF"))
                .andExpect(jsonPath("$.message").value("Seuls les fichiers PDF sont acceptés."));
    }

    @Test
    void renvoie504QuandLIaNeRepondPas() throws Exception {
        when(service.create(any(), any(), any(), any())).thenThrow(
                new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "Ollama n'a pas répondu."));

        mvc.perform(multipart(URL).file(cv).header(AUTH, USER_7).param("jobTitle", "Dev").param("jobDescription", DESCRIPTION))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("AI_TIMEOUT"));
    }

    // ── GET ─────────────────────────────────────────────────────────────────

    @Test
    void listeAvecLaPaginationParDefaut() throws Exception {
        when(service.list(eq(7L), any())).thenAnswer(inv -> new PageImpl<>(
                List.of(new AnalysisListItem(3L, "cv.pdf", "Dev Java", 72, LocalDateTime.of(2026, 10, 15, 10, 0))),
                inv.<Pageable>getArgument(1), 1));

        mvc.perform(get(URL).header(AUTH, USER_7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(3))
                .andExpect(jsonPath("$.content[0].jobTitle").value("Dev Java"))
                .andExpect(jsonPath("$.totalElements").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).list(eq(7L), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void renvoieLeDetailEn200() throws Exception {
        when(service.get(7L, 3L)).thenReturn(response(3, "Dev Java"));

        mvc.perform(get(URL + "/3").header(AUTH, USER_7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.summary").value("Bon profil."));
    }

    @Test
    void renvoie404PourUneAnalyseIntrouvable() throws Exception {
        when(service.get(7L, 3L)).thenThrow(
                new ApiException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", "Analyse introuvable : 3"));

        mvc.perform(get(URL + "/3").header(AUTH, USER_7))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));
    }

    @Test
    void renvoie400PourUnIdentifiantNonNumerique() throws Exception {
        mvc.perform(get(URL + "/abc").header(AUTH, USER_7))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void renvoie500SansDetailPourUneErreurInattendue() throws Exception {
        when(service.get(7L, 3L)).thenThrow(new IllegalStateException("Résultat stocké illisible pour l'analyse 3"));

        mvc.perform(get(URL + "/3").header(AUTH, USER_7))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Erreur interne du serveur."));
    }

    // ── PATCH / DELETE ──────────────────────────────────────────────────────

    @Test
    void renommeEtRenvoie200() throws Exception {
        when(service.rename(7L, 3L, "Lead Dev")).thenReturn(response(3, "Lead Dev"));

        mvc.perform(patch(URL + "/3").header(AUTH, USER_7)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"jobTitle\":\"Lead Dev\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Lead Dev"));
    }

    @Test
    void renvoie400PourUnRenommageVide() throws Exception {
        mvc.perform(patch(URL + "/3").header(AUTH, USER_7).contentType(MediaType.APPLICATION_JSON).content("{\"jobTitle\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("jobTitle : ")));
        verifyNoInteractions(service);
    }

    @Test
    void supprimeEtRenvoie204() throws Exception {
        mvc.perform(delete(URL + "/3").header(AUTH, USER_7))
                .andExpect(status().isNoContent());

        verify(service).delete(7L, 3L);
    }
}
