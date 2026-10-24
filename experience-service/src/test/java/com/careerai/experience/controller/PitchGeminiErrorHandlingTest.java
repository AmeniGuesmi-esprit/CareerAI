package com.careerai.experience.controller;

import com.careerai.experience.client.GeminiClient;
import com.careerai.experience.exception.GeminiException;
import com.careerai.experience.security.AuthUser;
import com.careerai.experience.service.PitchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'integration controleur + VRAI PitchService, avec un GeminiClient factice qui echoue.
 * Contrairement a PitchControllerTest (qui simule des exceptions que GeminiClient ne lance jamais),
 * on reproduit ici ce que le client leve reellement : GeminiException.
 */
@ExtendWith(MockitoExtension.class)
class PitchGeminiErrorHandlingTest {

    private static final String URL = "/api/pitch";
    private static final String BODY = "{\"experiences\":\"Stage de 6 mois chez Acme comme developpeur Java : "
            + "API REST, reduction du temps de reponse de 30%.\"}";

    @Mock
    private GeminiClient geminiClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthUser user = new AuthUser(7L, "laila@esprit.tn", "USER");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        PitchService service = new PitchService(geminiClient, new ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(new PitchController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void geminiFailsWith(GeminiException e) {
        when(geminiClient.generate(any(), any(), anyDouble(), any())).thenThrow(e);
    }

    @Test
    @DisplayName("Gemini injoignable apres retries (GeminiException <- ResourceAccessException) -> 503")
    void unreachable_returns503() throws Exception {
        geminiFailsWith(new GeminiException("Gemini injoignable apres 3 tentatives", new ResourceAccessException("timeout")));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Le service d'IA est temporairement indisponible"));
    }

    @Test
    @DisplayName("Timeout de lecture pendant le corps (GeminiException <- RestClientException <- SocketTimeoutException) -> 503")
    void readTimeoutDuringBody_returns503() throws Exception {
        geminiFailsWith(new GeminiException("Gemini : lecture de la reponse impossible apres 3 tentatives",
                new RestClientException("Error while extracting response", new SocketTimeoutException("Read timed out"))));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Le service d'IA est temporairement indisponible"));
    }

    @Test
    @DisplayName("Quota Gemini epuise apres retries (GeminiException <- HTTP 429) -> 503")
    void quota_returns503() throws Exception {
        geminiFailsWith(new GeminiException("Gemini indisponible apres 3 tentatives (dernier statut HTTP 429)",
                new RestClientResponseException("quota", 429, "Too Many Requests", null, null, null)));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Le service d'IA est temporairement surcharge"));
    }

    @Test
    @DisplayName("Erreur HTTP 400 de Gemini (non retentable) -> 502")
    void httpError_returns502() throws Exception {
        geminiFailsWith(new GeminiException("Gemini a repondu HTTP 400 : ...",
                new RestClientResponseException("bad", 400, "Bad Request", null, null, null)));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Erreur du service d'IA"));
    }

    @Test
    @DisplayName("Reponse vide / cle absente (GeminiException sans cause) -> 502, sans fuite du message interne")
    void noCause_returns502_withoutLeak() throws Exception {
        geminiFailsWith(new GeminiException("GEMINI_API_KEY n'est pas configuree"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Erreur du service d'IA"))
                .andExpect(jsonPath("$.detail").value(not(containsString("GEMINI_API_KEY"))));
    }
}
