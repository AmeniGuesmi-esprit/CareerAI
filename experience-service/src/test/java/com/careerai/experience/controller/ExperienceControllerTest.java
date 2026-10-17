package com.careerai.experience.controller;

import com.careerai.experience.config.GlobalExceptionHandler;
import com.careerai.experience.dto.ExperienceRequest;
import com.careerai.experience.dto.ExperienceResponse;
import com.careerai.experience.exception.BadRequestException;
import com.careerai.experience.exception.ResourceNotFoundException;
import com.careerai.experience.model.TypeContrat;
import com.careerai.experience.security.AuthUser;
import com.careerai.experience.service.ExperienceService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test "slice" leger : MockMvc autonome (sans contexte Spring complet, sans base de donnees, sans JWT).
 * L'utilisateur connecte est simule via le SecurityContext, comme le ferait JwtAuthFilter.
 */
@ExtendWith(MockitoExtension.class)
class ExperienceControllerTest {

    private static final long USER_ID = 7L;

    private static final String VALID_BODY = """
            {"poste":"Developpeur Java","entreprise":"Acme","lieu":"Tunis","typeContrat":"STAGE",
             "dateDebut":"2024-01-01","dateFin":"2024-06-30","enCours":false,"description":"API REST"}
            """;

    @Mock
    private ExperienceService experienceService;

    @InjectMocks
    private ExperienceController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthUser user = new AuthUser(USER_ID, "laila@esprit.tn", "USER");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static ExperienceResponse response(Long id, String poste) {
        return ExperienceResponse.builder()
                .id(id).poste(poste).entreprise("Acme").typeContrat(TypeContrat.STAGE).enCours(false).build();
    }

    // ---------- GET ----------

    @Test
    @DisplayName("GET /api/experiences -> 200 et liste des experiences de l'utilisateur connecte")
    void getAll_returnsList() throws Exception {
        when(experienceService.getAll(USER_ID)).thenReturn(List.of(response(1L, "Dev"), response(2L, "Testeur")));

        mockMvc.perform(get("/api/experiences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Liste des experiences"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].poste").value("Dev"))
                .andExpect(jsonPath("$.data[1].id").value(2));
    }

    @Test
    @DisplayName("GET /api/experiences/{id} -> 200")
    void getById_returnsExperience() throws Exception {
        when(experienceService.getById(USER_ID, 1L)).thenReturn(response(1L, "Dev"));

        mockMvc.perform(get("/api/experiences/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.typeContrat").value("STAGE"));
    }

    @Test
    @DisplayName("GET /api/experiences/{id} inexistant -> 404")
    void getById_notFound() throws Exception {
        when(experienceService.getById(USER_ID, 99L)).thenThrow(new ResourceNotFoundException("Experience introuvable"));

        mockMvc.perform(get("/api/experiences/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Experience introuvable"));
    }

    @Test
    @DisplayName("GET /api/experiences/abc (id non numerique) -> 400")
    void getById_invalidId() throws Exception {
        mockMvc.perform(get("/api/experiences/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Parametre invalide"));
    }

    // ---------- POST ----------

    @Test
    @DisplayName("POST /api/experiences valide -> 201 et userId pris du JWT")
    void create_returns201() throws Exception {
        when(experienceService.create(eq(USER_ID), any(ExperienceRequest.class))).thenReturn(response(10L, "Developpeur Java"));

        mockMvc.perform(post("/api/experiences").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Experience ajoutee"))
                .andExpect(jsonPath("$.data.id").value(10));

        ArgumentCaptor<ExperienceRequest> captor = ArgumentCaptor.forClass(ExperienceRequest.class);
        verify(experienceService).create(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().getPoste()).isEqualTo("Developpeur Java");
        assertThat(captor.getValue().getTypeContrat()).isEqualTo(TypeContrat.STAGE);
    }

    @Test
    @DisplayName("POST avec poste vide -> 400 avec le message de validation")
    void create_blankPoste_returns400() throws Exception {
        String body = VALID_BODY.replace("\"poste\":\"Developpeur Java\"", "\"poste\":\"\"");

        mockMvc.perform(post("/api/experiences").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Le poste est obligatoire"));

        verify(experienceService, never()).create(any(), any());
    }

    @Test
    @DisplayName("POST sans type de contrat -> 400")
    void create_missingTypeContrat_returns400() throws Exception {
        String body = VALID_BODY.replace("\"typeContrat\":\"STAGE\",", "");

        mockMvc.perform(post("/api/experiences").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le type de contrat est obligatoire"));
    }

    @Test
    @DisplayName("POST avec un type de contrat inconnu -> 400")
    void create_unknownTypeContrat_returns400() throws Exception {
        String body = VALID_BODY.replace("STAGE", "INCONNU");

        mockMvc.perform(post("/api/experiences").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Corps de requete invalide")));
    }

    @Test
    @DisplayName("POST avec une date au mauvais format -> 400")
    void create_badDateFormat_returns400() throws Exception {
        String body = VALID_BODY.replace("2024-01-01", "01/01/2024");

        mockMvc.perform(post("/api/experiences").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("AAAA-MM-JJ")));
    }

    @Test
    @DisplayName("POST avec JSON malforme -> 400")
    void create_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/experiences").contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST : BadRequestException du service (ex. dates incoherentes) -> 400")
    void create_serviceBadRequest_returns400() throws Exception {
        when(experienceService.create(eq(USER_ID), any(ExperienceRequest.class)))
                .thenThrow(new BadRequestException("La date de fin doit etre posterieure a la date de debut"));

        mockMvc.perform(post("/api/experiences").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La date de fin doit etre posterieure a la date de debut"));
    }

    // ---------- PUT ----------

    @Test
    @DisplayName("PUT /api/experiences/{id} -> 200")
    void update_returns200() throws Exception {
        when(experienceService.update(eq(USER_ID), eq(3L), any(ExperienceRequest.class))).thenReturn(response(3L, "Developpeur Java"));

        mockMvc.perform(put("/api/experiences/3").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Experience modifiee"))
                .andExpect(jsonPath("$.data.id").value(3));
    }

    @Test
    @DisplayName("PUT sur une experience inexistante -> 404")
    void update_notFound() throws Exception {
        when(experienceService.update(eq(USER_ID), eq(3L), any(ExperienceRequest.class)))
                .thenThrow(new ResourceNotFoundException("Experience introuvable"));

        mockMvc.perform(put("/api/experiences/3").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT avec corps invalide -> 400 sans appeler le service")
    void update_invalidBody_returns400() throws Exception {
        String body = VALID_BODY.replace("\"entreprise\":\"Acme\"", "\"entreprise\":\"\"");

        mockMvc.perform(put("/api/experiences/3").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("L'entreprise est obligatoire"));

        verify(experienceService, never()).update(any(), any(), any());
    }

    // ---------- DELETE ----------

    @Test
    @DisplayName("DELETE /api/experiences/{id} -> 200 et delegue au service avec l'userId du JWT")
    void delete_returns200() throws Exception {
        mockMvc.perform(delete("/api/experiences/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Experience supprimee"));

        verify(experienceService).delete(USER_ID, 3L);
    }

    @Test
    @DisplayName("DELETE d'une experience inexistante -> 404")
    void delete_notFound() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Experience introuvable"))
                .when(experienceService).delete(USER_ID, 3L);

        mockMvc.perform(delete("/api/experiences/3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
