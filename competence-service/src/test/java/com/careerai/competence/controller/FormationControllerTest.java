package com.careerai.competence.controller;

import com.careerai.competence.dto.ApiResponse;
import com.careerai.competence.dto.FormationRequest;
import com.careerai.competence.dto.FormationResponse;
import com.careerai.competence.exception.BadRequestException;
import com.careerai.competence.exception.ResourceNotFoundException;
import com.careerai.competence.service.FormationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Test unitaire : le service est mocké, on vérifie statut HTTP, message et délégation (userId du JWT). */
@ExtendWith(MockitoExtension.class)
class FormationControllerTest {

    @Mock FormationService service;
    @InjectMocks FormationController controller;

    private static FormationResponse resp(long id) {
        return FormationResponse.builder().id(id).diplome("Licence").etablissement("ESPRIT")
                .dateDebut(LocalDate.of(2023, 9, 1)).dateFin(LocalDate.of(2026, 6, 30)).build();
    }

    private static FormationRequest req() {
        FormationRequest r = new FormationRequest();
        r.setDiplome("Licence");
        r.setEtablissement("ESPRIT");
        r.setDateDebut(LocalDate.of(2023, 9, 1));
        r.setDateFin(LocalDate.of(2026, 6, 30));
        return r;
    }

    @Test
    void list_returns200WithData() {
        when(service.findAll(1L)).thenReturn(List.of(resp(1)));

        ResponseEntity<ApiResponse<List<FormationResponse>>> res = controller.list(1L);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().getData()).hasSize(1);
    }

    @Test
    void get_returns200() {
        when(service.findById(1L, 3L)).thenReturn(resp(3));

        assertThat(controller.get(1L, 3L).getBody().getData().getId()).isEqualTo(3L);
    }

    @Test
    void get_notFound_exceptionPropagates() {
        when(service.findById(2L, 3L)).thenThrow(new ResourceNotFoundException("Formation introuvable"));

        assertThatThrownBy(() -> controller.get(2L, 3L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_returns201() {
        FormationRequest r = req();
        when(service.create(1L, r)).thenReturn(resp(4));

        var res = controller.create(1L, r);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().getMessage()).isEqualTo("Formation ajoutée");
    }

    @Test
    void create_invalidDates_exceptionPropagates() {
        FormationRequest r = req();
        when(service.create(1L, r)).thenThrow(new BadRequestException("dates"));

        assertThatThrownBy(() -> controller.create(1L, r)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_returns200() {
        FormationRequest r = req();
        when(service.update(1L, 3L, r)).thenReturn(resp(3));

        var res = controller.update(1L, 3L, r);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().getMessage()).isEqualTo("Formation modifiée");
    }

    @Test
    void delete_returns200AndDelegatesWithUserId() {
        var res = controller.delete(1L, 3L);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().getMessage()).isEqualTo("Formation supprimée");
        verify(service).delete(1L, 3L);
    }
}
