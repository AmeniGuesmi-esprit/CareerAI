package com.careerai.competence.controller;

import com.careerai.competence.dto.ApiResponse;
import com.careerai.competence.dto.CompetenceRequest;
import com.careerai.competence.dto.CompetenceResponse;
import com.careerai.competence.exception.ConflictException;
import com.careerai.competence.exception.ResourceNotFoundException;
import com.careerai.competence.model.CategorieCompetence;
import com.careerai.competence.model.NiveauCompetence;
import com.careerai.competence.service.CompetenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Test unitaire : le service est mocké, on vérifie statut HTTP, message et délégation (userId du JWT). */
@ExtendWith(MockitoExtension.class)
class CompetenceControllerTest {

    @Mock CompetenceService service;
    @InjectMocks CompetenceController controller;

    private static CompetenceResponse resp(long id) {
        return CompetenceResponse.builder().id(id).nom("Java")
                .niveau(NiveauCompetence.AVANCE).categorie(CategorieCompetence.TECHNIQUE).build();
    }

    private static CompetenceRequest req() {
        CompetenceRequest r = new CompetenceRequest();
        r.setNom("Java");
        r.setNiveau(NiveauCompetence.AVANCE);
        r.setCategorie(CategorieCompetence.TECHNIQUE);
        return r;
    }

    @Test
    void list_returns200WithData() {
        when(service.findAll(1L)).thenReturn(List.of(resp(1), resp(2)));

        ResponseEntity<ApiResponse<List<CompetenceResponse>>> res = controller.list(1L);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().isSuccess()).isTrue();
        assertThat(res.getBody().getData()).hasSize(2);
    }

    @Test
    void get_returns200() {
        when(service.findById(1L, 5L)).thenReturn(resp(5));

        var res = controller.get(1L, 5L);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().getData().getId()).isEqualTo(5L);
    }

    @Test
    void get_notFound_exceptionPropagates() {
        when(service.findById(1L, 9L)).thenThrow(new ResourceNotFoundException("Compétence introuvable"));

        assertThatThrownBy(() -> controller.get(1L, 9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_returns201() {
        CompetenceRequest r = req();
        when(service.create(1L, r)).thenReturn(resp(7));

        var res = controller.create(1L, r);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().getMessage()).isEqualTo("Compétence ajoutée");
        assertThat(res.getBody().getData().getId()).isEqualTo(7L);
    }

    @Test
    void create_conflict_exceptionPropagates() {
        CompetenceRequest r = req();
        when(service.create(1L, r)).thenThrow(new ConflictException("Cette compétence existe déjà"));

        assertThatThrownBy(() -> controller.create(1L, r)).isInstanceOf(ConflictException.class);
    }

    @Test
    void update_returns200() {
        CompetenceRequest r = req();
        when(service.update(1L, 5L, r)).thenReturn(resp(5));

        var res = controller.update(1L, 5L, r);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().getMessage()).isEqualTo("Compétence modifiée");
    }

    @Test
    void delete_returns200AndDelegatesWithUserId() {
        var res = controller.delete(1L, 5L);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().getMessage()).isEqualTo("Compétence supprimée");
        assertThat(res.getBody().getData()).isNull();
        verify(service).delete(1L, 5L);
    }
}
