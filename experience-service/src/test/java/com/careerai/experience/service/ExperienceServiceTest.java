package com.careerai.experience.service;

import com.careerai.experience.dto.ExperienceRequest;
import com.careerai.experience.dto.ExperienceResponse;
import com.careerai.experience.exception.BadRequestException;
import com.careerai.experience.exception.ResourceNotFoundException;
import com.careerai.experience.model.Experience;
import com.careerai.experience.model.TypeContrat;
import com.careerai.experience.repository.ExperienceRepository;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperienceServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long OTHER_USER_ID = 99L;

    @Mock
    private ExperienceRepository repository;

    @InjectMocks
    private ExperienceService service;

    // ---------- helpers ----------

    private static ExperienceRequest validRequest() {
        ExperienceRequest r = new ExperienceRequest();
        r.setPoste("  Developpeur Java  ");
        r.setEntreprise("  Acme  ");
        r.setLieu("  Tunis  ");
        r.setTypeContrat(TypeContrat.STAGE);
        r.setDateDebut(LocalDate.of(2024, 1, 1));
        r.setDateFin(LocalDate.of(2024, 6, 30));
        r.setEnCours(false);
        r.setDescription("  API REST avec Spring Boot  ");
        return r;
    }

    private static Experience storedExperience(Long id, Long userId) {
        return Experience.builder()
                .id(id)
                .userId(userId)
                .poste("Developpeur")
                .entreprise("Acme")
                .lieu("Tunis")
                .typeContrat(TypeContrat.CDI)
                .dateDebut(LocalDate.of(2023, 1, 1))
                .dateFin(LocalDate.of(2023, 12, 31))
                .enCours(false)
                .description("desc")
                .build();
    }

    // ---------- getAll ----------

    @Test
    @DisplayName("getAll : retourne les experiences de l'utilisateur sous forme de DTO")
    void getAll_returnsMappedExperiences() {
        when(repository.findByUserIdOrderByDateDebutDesc(USER_ID))
                .thenReturn(List.of(storedExperience(1L, USER_ID), storedExperience(2L, USER_ID)));

        List<ExperienceResponse> result = service.getAll(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ExperienceResponse::getId).containsExactly(1L, 2L);
        assertThat(result.get(0).getPoste()).isEqualTo("Developpeur");
    }

    @Test
    @DisplayName("getAll : liste vide quand l'utilisateur n'a aucune experience")
    void getAll_returnsEmptyList() {
        when(repository.findByUserIdOrderByDateDebutDesc(USER_ID)).thenReturn(List.of());

        assertThat(service.getAll(USER_ID)).isEmpty();
    }

    // ---------- getById ----------

    @Test
    @DisplayName("getById : retourne l'experience quand elle appartient a l'utilisateur")
    void getById_found() {
        when(repository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(storedExperience(1L, USER_ID)));

        ExperienceResponse result = service.getById(USER_ID, 1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEntreprise()).isEqualTo("Acme");
    }

    @Test
    @DisplayName("getById : 404 quand l'experience n'existe pas ou appartient a un autre utilisateur")
    void getById_notFound() {
        when(repository.findByIdAndUserId(1L, OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(OTHER_USER_ID, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Experience introuvable");
    }

    // ---------- create ----------

    @Test
    @DisplayName("create : associe l'userId du JWT, nettoie les champs et sauvegarde")
    void create_setsOwnerAndTrimsFields() {
        when(repository.save(any(Experience.class))).thenAnswer(inv -> inv.getArgument(0));

        ExperienceResponse result = service.create(USER_ID, validRequest());

        ArgumentCaptor<Experience> captor = ArgumentCaptor.forClass(Experience.class);
        verify(repository).save(captor.capture());
        Experience saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getPoste()).isEqualTo("Developpeur Java");
        assertThat(saved.getEntreprise()).isEqualTo("Acme");
        assertThat(saved.getLieu()).isEqualTo("Tunis");
        assertThat(saved.getDescription()).isEqualTo("API REST avec Spring Boot");
        assertThat(saved.getTypeContrat()).isEqualTo(TypeContrat.STAGE);
        assertThat(saved.getDateFin()).isEqualTo(LocalDate.of(2024, 6, 30));
        assertThat(result.getPoste()).isEqualTo("Developpeur Java");
    }

    @Test
    @DisplayName("create : lieu et description vides ou blancs deviennent null")
    void create_blankOptionalFieldsBecomeNull() {
        when(repository.save(any(Experience.class))).thenAnswer(inv -> inv.getArgument(0));
        ExperienceRequest request = validRequest();
        request.setLieu("   ");
        request.setDescription("");

        service.create(USER_ID, request);

        ArgumentCaptor<Experience> captor = ArgumentCaptor.forClass(Experience.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getLieu()).isNull();
        assertThat(captor.getValue().getDescription()).isNull();
    }

    @Test
    @DisplayName("create : lieu et description null restent null")
    void create_nullOptionalFieldsStayNull() {
        when(repository.save(any(Experience.class))).thenAnswer(inv -> inv.getArgument(0));
        ExperienceRequest request = validRequest();
        request.setLieu(null);
        request.setDescription(null);

        service.create(USER_ID, request);

        ArgumentCaptor<Experience> captor = ArgumentCaptor.forClass(Experience.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getLieu()).isNull();
        assertThat(captor.getValue().getDescription()).isNull();
    }

    @Test
    @DisplayName("create : si enCours = true, la date de fin est ignoree (null)")
    void create_enCoursForcesDateFinToNull() {
        when(repository.save(any(Experience.class))).thenAnswer(inv -> inv.getArgument(0));
        ExperienceRequest request = validRequest();
        request.setEnCours(true);
        request.setDateFin(LocalDate.of(2024, 6, 30)); // doit etre ignoree

        ExperienceResponse result = service.create(USER_ID, request);

        assertThat(result.isEnCours()).isTrue();
        assertThat(result.getDateFin()).isNull();
    }

    @Test
    @DisplayName("create : si enCours = true, pas besoin de date de fin")
    void create_enCoursWithoutDateFinIsValid() {
        when(repository.save(any(Experience.class))).thenAnswer(inv -> inv.getArgument(0));
        ExperienceRequest request = validRequest();
        request.setEnCours(true);
        request.setDateFin(null);

        ExperienceResponse result = service.create(USER_ID, request);

        assertThat(result.isEnCours()).isTrue();
        assertThat(result.getDateFin()).isNull();
    }

    @Test
    @DisplayName("create : BadRequest si pas en cours et date de fin absente")
    void create_missingDateFin_throwsBadRequest() {
        ExperienceRequest request = validRequest();
        request.setEnCours(false);
        request.setDateFin(null);

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("date de fin est obligatoire");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create : BadRequest si la date de fin precede la date de debut")
    void create_dateFinBeforeDateDebut_throwsBadRequest() {
        ExperienceRequest request = validRequest();
        request.setDateDebut(LocalDate.of(2024, 6, 30));
        request.setDateFin(LocalDate.of(2024, 1, 1));

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("posterieure");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create : date de fin egale a la date de debut acceptee")
    void create_dateFinEqualsDateDebut_isValid() {
        when(repository.save(any(Experience.class))).thenAnswer(inv -> inv.getArgument(0));
        ExperienceRequest request = validRequest();
        request.setDateDebut(LocalDate.of(2024, 3, 1));
        request.setDateFin(LocalDate.of(2024, 3, 1));

        ExperienceResponse result = service.create(USER_ID, request);

        assertThat(result.getDateFin()).isEqualTo(LocalDate.of(2024, 3, 1));
    }

    // ---------- update ----------

    @Test
    @DisplayName("update : modifie l'experience existante de l'utilisateur")
    void update_updatesOwnedExperience() {
        Experience existing = storedExperience(5L, USER_ID);
        when(repository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(Experience.class))).thenAnswer(inv -> inv.getArgument(0));

        ExperienceResponse result = service.update(USER_ID, 5L, validRequest());

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getPoste()).isEqualTo("Developpeur Java");
        assertThat(result.getTypeContrat()).isEqualTo(TypeContrat.STAGE);
        assertThat(existing.getUserId()).isEqualTo(USER_ID); // le proprietaire ne change jamais
        verify(repository).save(existing);
    }

    @Test
    @DisplayName("update : 404 si l'experience appartient a un autre utilisateur")
    void update_notOwned_throwsNotFound() {
        when(repository.findByIdAndUserId(5L, OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(OTHER_USER_ID, 5L, validRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update : les dates sont validees avant toute lecture en base")
    void update_invalidDates_throwsBadRequestWithoutQuery() {
        ExperienceRequest request = validRequest();
        request.setDateFin(null);

        assertThatThrownBy(() -> service.update(USER_ID, 5L, request))
                .isInstanceOf(BadRequestException.class);
        verify(repository, never()).findByIdAndUserId(any(), any());
        verify(repository, never()).save(any());
    }

    // ---------- delete ----------

    @Test
    @DisplayName("delete : supprime l'experience de l'utilisateur")
    void delete_deletesOwnedExperience() {
        Experience existing = storedExperience(3L, USER_ID);
        when(repository.findByIdAndUserId(3L, USER_ID)).thenReturn(Optional.of(existing));

        service.delete(USER_ID, 3L);

        verify(repository).delete(existing);
    }

    @Test
    @DisplayName("delete : 404 et aucune suppression si l'experience n'appartient pas a l'utilisateur")
    void delete_notOwned_throwsNotFound() {
        when(repository.findByIdAndUserId(3L, OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(OTHER_USER_ID, 3L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete(any(Experience.class));
    }
}
