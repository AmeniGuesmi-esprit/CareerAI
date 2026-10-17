package com.careerai.competence.controller;

import com.careerai.competence.ai.AdvisorService;
import com.careerai.competence.dto.AdviceResponse;
import com.careerai.competence.exception.AiException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrôleur + sécurité + validation, avec l'IA mockée (aucun appel Gemini). */
@SpringBootTest
@AutoConfigureMockMvc
class OrientationControllerTest {

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secret;
    @MockBean AdvisorService advisor;

    private static final String BODY = "{\"prompt\":\"Quelles certifications pour devenir développeur cloud ?\"}";

    private String bearer(long userId) {
        return "Bearer " + Jwts.builder()
                .setClaims(Map.of("userId", userId, "role", "USER"))
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret)), SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void withoutToken_401() throws Exception {
        mvc.perform(post("/api/orientation/advice").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validRequest_200_returnsAdvice() throws Exception {
        when(advisor.advise(eq(7L), any())).thenReturn(new AdviceResponse("Conseil", "VERIFIEE", null));

        mvc.perform(post("/api/orientation/advice").header("Authorization", bearer(7))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reponse").value("Conseil"))
                .andExpect(jsonPath("$.data.statut").value("VERIFIEE"));
    }

    @Test
    void tooShortPrompt_400() throws Exception {
        mvc.perform(post("/api/orientation/advice").header("Authorization", bearer(7))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"court\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void blankPrompt_400() throws Exception {
        mvc.perform(post("/api/orientation/advice").header("Authorization", bearer(7))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aiRateLimited_429() throws Exception {
        when(advisor.advise(any(), any())).thenThrow(new AiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de demandes"));

        mvc.perform(post("/api/orientation/advice").header("Authorization", bearer(7))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Trop de demandes"));
    }
}
