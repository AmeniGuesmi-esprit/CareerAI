package com.careerai.competence.service;

import com.careerai.competence.dto.FormationRequest;
import com.careerai.competence.dto.FormationResponse;
import com.careerai.competence.exception.BadRequestException;
import com.careerai.competence.exception.ResourceNotFoundException;
import com.careerai.competence.model.Formation;
import com.careerai.competence.repository.FormationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FormationServiceTest {

    @Mock FormationRepository repository;
    @InjectMocks FormationService service;

    private static FormationRequest request(LocalDate debut, LocalDate fin, boolean enCours) {
        FormationRequest r = new FormationRequest();
        r.setDiplome("  Licence SAE ");
        r.setEtablissement(" ESPRIT ");
        r.setDomaine("Informatique");
        r.setDateDebut(debut);
        r.setDateFin(fin);
        r.setEnCours(enCours);
        r.setDescription("desc");
        return r;
    }

    private static Formation entity(Long id, Long userId) {
        return Formation.builder().id(id).userId(userId).diplome("Licence").etablissement("ESPRIT")
                .dateDebut(LocalDate.of(2023, 9, 1)).dateFin(LocalDate.of(2026, 6, 30)).build();
    }

    @Test
    void findAll_mapsResults() {
        when(repository.findByUserIdOrderByDateDebutDesc(1L)).thenReturn(List.of(entity(1L, 1L), entity(2L, 1L)));

        assertThat(service.findAll(1L)).hasSize(2);
    }

    @Test
    void findById_notOwned_throwsNotFound() {
        when(repository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(1L, 9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_trimsFieldsAndAssignsUser() {
        when(repository.save(any(Formation.class))).thenAnswer(inv -> inv.getArgument(0));

        FormationResponse res = service.create(1L,
                request(LocalDate.of(2023, 9, 1), LocalDate.of(2026, 6, 30), false));

        ArgumentCaptor<Formation> captor = ArgumentCaptor.forClass(Formation.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(res.getDiplome()).isEqualTo("Licence SAE");
        assertThat(res.getEtablissement()).isEqualTo("ESPRIT");
        assertThat(res.getDateFin()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void create_blankDomaine_becomesNull() {
        when(repository.save(any(Formation.class))).thenAnswer(inv -> inv.getArgument(0));
        FormationRequest req = request(LocalDate.of(2023, 9, 1), LocalDate.of(2026, 6, 30), false);
        req.setDomaine("   ");

        assertThat(service.create(1L, req).getDomaine()).isNull();
    }

    @Test
    void create_enCoursWithoutEndDate_ok() {
        when(repository.save(any(Formation.class))).thenAnswer(inv -> inv.getArgument(0));

        FormationResponse res = service.create(1L, request(LocalDate.of(2024, 9, 1), null, true));

        assertThat(res.isEnCours()).isTrue();
        assertThat(res.getDateFin()).isNull();
    }

    @Test
    void create_enCoursWithEndDate_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(1L,
                request(LocalDate.of(2024, 9, 1), LocalDate.of(2025, 6, 1), true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("en cours");
        verify(repository, never()).save(any());
    }

    @Test
    void create_notEnCoursWithoutEndDate_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(1L, request(LocalDate.of(2024, 9, 1), null, false)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("obligatoire");
    }

    @Test
    void create_endBeforeStart_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(1L,
                request(LocalDate.of(2024, 9, 1), LocalDate.of(2023, 1, 1), false)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("postérieure");
    }

    @Test
    void create_endEqualsStart_isAccepted() {
        when(repository.save(any(Formation.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate d = LocalDate.of(2024, 9, 1);
        assertThat(service.create(1L, request(d, d, false)).getDateFin()).isEqualTo(d);
    }

    @Test
    void update_ok_setsUpdatedAt() {
        Formation existing = entity(3L, 1L);
        when(repository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        FormationResponse res = service.update(1L, 3L,
                request(LocalDate.of(2023, 9, 1), LocalDate.of(2026, 6, 30), false));

        assertThat(res.getDiplome()).isEqualTo("Licence SAE");
        assertThat(existing.getUpdatedAt()).isNotNull();
    }

    @Test
    void update_invalidDates_doesNotTouchRepository() {
        assertThatThrownBy(() -> service.update(1L, 3L, request(LocalDate.of(2024, 9, 1), null, false)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void update_notOwned_throwsNotFound() {
        when(repository.findByIdAndUserId(3L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(2L, 3L,
                request(LocalDate.of(2023, 9, 1), LocalDate.of(2026, 6, 30), false)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_ok() {
        Formation f = entity(3L, 1L);
        when(repository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(f));

        service.delete(1L, 3L);

        verify(repository).delete(f);
    }

    @Test
    void delete_notOwned_throwsNotFound() {
        when(repository.findByIdAndUserId(3L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(2L, 3L)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete(any(Formation.class));
    }
}
