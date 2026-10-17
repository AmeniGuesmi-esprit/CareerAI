package com.careerai.experience.controller;

import com.careerai.experience.dto.PitchRequest;
import com.careerai.experience.dto.PitchResponse;
import com.careerai.experience.exception.PitchException;
import com.careerai.experience.exception.RateLimitException;
import com.careerai.experience.security.AuthUser;
import com.careerai.experience.service.PitchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Les @ExceptionHandler sont dans PitchController : ils sont donc actifs sans ControllerAdvice. */
@ExtendWith(MockitoExtension.class)
class PitchControllerTest {

    private static final long USER_ID = 7L;
    private static final String URL = "/api/pitch";

    private static final String EXPERIENCES =
            "Stage de 6 mois chez Acme comme developpeur Java : API REST, reduction du temps de reponse de 30%.";
    private static final String VALID_BODY = "{\"experiences\":\"" + EXPERIENCES + "\"}";

    @Mock
    private PitchService pitchService;

    @InjectMocks
    private PitchController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthUser user = new AuthUser(USER_ID, "laila@esprit.tn", "USER");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------- succes ----------

    @Test
    @DisplayName("POST /api/pitch valide -> 200 avec les 3 versions du pitch")
    void generate_returnsPitch() throws Exception {
        when(pitchService.generate(eq(USER_ID), any(PitchRequest.class)))
                .thenReturn(new PitchResponse("version courte", "version standard", "version enthousiaste"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Pitch genere"))
                .andExpect(jsonPath("$.data.courte").value("version courte"))
                .andExpect(jsonPath("$.data.standard").value("version standard"))
                .andExpect(jsonPath("$.data.enthousiaste").value("version enthousiaste"));

        ArgumentCaptor<PitchRequest> captor = ArgumentCaptor.forClass(PitchRequest.class);
        verify(pitchService).generate(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().getExperiences()).isEqualTo(EXPERIENCES);
    }

    // ---------- 400 ----------

    @Test
    @DisplayName("texte trop court -> 400 avec le message de validation, service non appele")
    void generate_tooShort_returns400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"experiences\":\"trop court\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("entre 50 et 5000")));

        verify(pitchService, never()).generate(anyLong(), any());
    }

    @Test
    @DisplayName("texte absent -> 400")
    void generate_missingText_returns400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());

        verify(pitchService, never()).generate(anyLong(), any());
    }

    @Test
    @DisplayName("corps absent -> 400")
    void generate_noBody_returns400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Corps de la requete invalide ou JSON malforme"));
    }

    @Test
    @DisplayName("JSON malforme -> 400")
    void generate_malformedJson_returns400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Corps de la requete invalide ou JSON malforme"));
    }

    @Test
    @DisplayName("IllegalArgumentException du service (aucune experience exploitable) -> 400")
    void generate_illegalArgument_returns400() throws Exception {
        when(pitchService.generate(eq(USER_ID), any(PitchRequest.class)))
                .thenThrow(new IllegalArgumentException("Aucune experience exploitable n'a ete trouvee dans le texte"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Aucune experience exploitable n'a ete trouvee dans le texte"));
    }

    // ---------- 429 ----------

    @Test
    @DisplayName("RateLimitException -> 429 avec en-tete Retry-After")
    void generate_rateLimited_returns429() throws Exception {
        when(pitchService.generate(eq(USER_ID), any(PitchRequest.class)))
                .thenThrow(new RateLimitException("Limite de 10 requetes par minute depassee.", 12));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "12"))
                .andExpect(jsonPath("$.detail").value("Limite de 10 requetes par minute depassee."));
    }

    // ---------- 502 / 503 ----------

    @Test
    @DisplayName("PitchException (pitch non fiable) -> 502")
    void generate_pitchException_returns502() throws Exception {
        when(pitchService.generate(eq(USER_ID), any(PitchRequest.class)))
                .thenThrow(new PitchException("Impossible de generer un pitch fiable apres 2 tentatives"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Impossible de generer un pitch fiable apres 2 tentatives"));
    }

    @Test
    @DisplayName("ResourceAccessException (Gemini injoignable) -> 503")
    void generate_geminiUnreachable_returns503() throws Exception {
        when(pitchService.generate(eq(USER_ID), any(PitchRequest.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Le service d'IA est temporairement indisponible"));
    }

    @Test
    @DisplayName("Erreur HTTP 429 de Gemini -> 503 (service surcharge)")
    void generate_geminiQuota_returns503() throws Exception {
        when(pitchService.generate(eq(USER_ID), any(PitchRequest.class)))
                .thenThrow(new RestClientResponseException("quota", 429, "Too Many Requests", null, null, null));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Le service d'IA est temporairement surcharge"));
    }

    @Test
    @DisplayName("Autre erreur HTTP de Gemini (500) -> 502")
    void generate_geminiServerError_returns502() throws Exception {
        when(pitchService.generate(eq(USER_ID), any(PitchRequest.class)))
                .thenThrow(new RestClientResponseException("boom", 500, "Internal Server Error", null, null, null));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Erreur du service d'IA"));
    }

    @Test
    @DisplayName("RestClientException generique -> 502")
    void generate_otherClientError_returns502() throws Exception {
        when(pitchService.generate(eq(USER_ID), any(PitchRequest.class)))
                .thenThrow(new RestClientException("erreur inattendue"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Erreur du service d'IA"));
    }
}
