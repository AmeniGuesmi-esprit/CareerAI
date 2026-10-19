package com.careerai.competence.service;

import com.careerai.competence.dto.CompetenceRequest;
import com.careerai.competence.dto.CompetenceResponse;
import com.careerai.competence.exception.ConflictException;
import com.careerai.competence.exception.ResourceNotFoundException;
import com.careerai.competence.model.CategorieCompetence;
import com.careerai.competence.model.Competence;
import com.careerai.competence.model.NiveauCompetence;
import com.careerai.competence.repository.CompetenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompetenceServiceTest {

    @Mock CompetenceRepository repository;
    @InjectMocks CompetenceService service;

    private static CompetenceRequest request(String nom, NiveauCompetence n, CategorieCompetence c) {
        CompetenceRequest r = new CompetenceRequest();
        r.setNom(nom);
        r.setNiveau(n);
        r.setCategorie(c);
        return r;
    }

    private static Competence entity(Long id, Long userId, String nom) {
        return Competence.builder().id(id).userId(userId).nom(nom)
                .niveau(NiveauCompetence.AVANCE).categorie(CategorieCompetence.TECHNIQUE).build();
    }

    @Test
    void findAll_returnsMappedCompetencesOfUser() {
        when(repository.findByUserIdOrderByNomAsc(1L))
                .thenReturn(List.of(entity(1L, 1L, "Java"), entity(2L, 1L, "Spring")));

        List<CompetenceResponse> result = service.findAll(1L);

        assertThat(result).extracting(CompetenceResponse::getNom).containsExactly("Java", "Spring");
    }

    @Test
    void findById_ok() {
        when(repository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(entity(5L, 1L, "Java")));

        assertThat(service.findById(1L, 5L).getNom()).isEqualTo("Java");
    }

    @Test
    void findById_notOwned_throwsNotFound() {
        when(repository.findByIdAndUserId(5L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(2L, 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_trimsNameAndSaves() {
        when(repository.existsByUserIdAndNomIgnoreCase(1L, "Docker")).thenReturn(false);
        when(repository.save(any(Competence.class))).thenAnswer(inv -> inv.getArgument(0));

        CompetenceResponse res = service.create(1L,
                request("  Docker  ", NiveauCompetence.DEBUTANT, CategorieCompetence.OUTIL));

        ArgumentCaptor<Competence> captor = ArgumentCaptor.forClass(Competence.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getNom()).isEqualTo("Docker");
        assertThat(res.getNiveau()).isEqualTo(NiveauCompetence.DEBUTANT);
        assertThat(res.getCategorie()).isEqualTo(CategorieCompetence.OUTIL);
    }

    @Test
    void create_duplicate_throwsConflict_andDoesNotSave() {
        when(repository.existsByUserIdAndNomIgnoreCase(1L, "Java")).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L,
                request("Java", NiveauCompetence.AVANCE, CategorieCompetence.TECHNIQUE)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_modifiesFieldsAndSetsUpdatedAt() {
        Competence existing = entity(5L, 1L, "Java");
        when(repository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(existing));
        when(repository.existsByUserIdAndNomIgnoreCaseAndIdNot(1L, "Java 17", 5L)).thenReturn(false);
        when(repository.save(existing)).thenReturn(existing);

        CompetenceResponse res = service.update(1L, 5L,
                request(" Java 17 ", NiveauCompetence.EXPERT, CategorieCompetence.TECHNIQUE));

        assertThat(res.getNom()).isEqualTo("Java 17");
        assertThat(res.getNiveau()).isEqualTo(NiveauCompetence.EXPERT);
        assertThat(existing.getUpdatedAt()).isNotNull();
    }

    @Test
    void update_duplicateName_throwsConflict() {
        when(repository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(entity(5L, 1L, "Java")));
        when(repository.existsByUserIdAndNomIgnoreCaseAndIdNot(1L, "Spring", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, 5L,
                request("Spring", NiveauCompetence.AVANCE, CategorieCompetence.TECHNIQUE)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_notOwned_throwsNotFound() {
        when(repository.findByIdAndUserId(5L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(2L, 5L,
                request("X", NiveauCompetence.AVANCE, CategorieCompetence.AUTRE)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_ok() {
        Competence c = entity(5L, 1L, "Java");
        when(repository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(c));

        service.delete(1L, 5L);

        verify(repository).delete(c);
    }

    @Test
    void delete_notOwned_throwsNotFound_andDoesNotDelete() {
        when(repository.findByIdAndUserId(5L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(2L, 5L)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete(any(Competence.class));
    }
}
