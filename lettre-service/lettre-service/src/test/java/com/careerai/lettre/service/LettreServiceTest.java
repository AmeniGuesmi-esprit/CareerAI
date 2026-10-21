package com.careerai.lettre.service;

import com.careerai.lettre.dto.ApiResponse;
import com.careerai.lettre.dto.LettreRequest;
import com.careerai.lettre.dto.LettreResponse;
import com.careerai.lettre.model.Lettre;
import com.careerai.lettre.repository.LettreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CRUD des lettres : chacun ne voit, ne modifie et ne supprime que ses propres lettres. Base simulée. */
@ExtendWith(MockitoExtension.class)
class LettreServiceTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";

    @Mock
    LettreRepository repo;

    @InjectMocks
    LettreService service;

    private static LettreRequest request(String entreprise, String poste) {
        LettreRequest r = new LettreRequest();
        r.setNomEntreprise(entreprise);
        r.setPoste(poste);
        r.setCv("  Mon CV  ");
        r.setContenu("  Madame, Monsieur,  ");
        return r;
    }

    private static Lettre lettre(long id, String owner) {
        return Lettre.builder().id(id).userEmail(owner).nomEntreprise("Acme").poste("Dev")
                .cv("cv").contenu("lettre").build();
    }

    // ───────────────────────── Création ─────────────────────────

    @Test
    void createTakesTheOwnerFromTheArgumentAndTrimsTheFields() {
        ApiResponse<LettreResponse> res = service.create(ALICE, request("  Acme  ", "   "));

        ArgumentCaptor<Lettre> saved = ArgumentCaptor.forClass(Lettre.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getUserEmail()).isEqualTo(ALICE);
        assertThat(saved.getValue().getNomEntreprise()).isEqualTo("Acme");
        assertThat(saved.getValue().getPoste()).isNull();
        assertThat(saved.getValue().getCv()).isEqualTo("Mon CV");
        assertThat(saved.getValue().getContenu()).isEqualTo("Madame, Monsieur,");
        assertThat(res.isSuccess()).isTrue();
    }

    @Test
    void createKeepsAProvidedJobTitle() {
        service.create(ALICE, request("Acme", " Développeur Java "));

        ArgumentCaptor<Lettre> saved = ArgumentCaptor.forClass(Lettre.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getPoste()).isEqualTo("Développeur Java");
    }

    // ───────────────────────── Lecture ─────────────────────────

    @Test
    void getMineOnlyAsksForTheCallersLetters() {
        when(repo.findByUserEmailOrderByCreatedAtDesc(ALICE)).thenReturn(List.of(lettre(1, ALICE), lettre(2, ALICE)));

        ApiResponse<List<LettreResponse>> res = service.getMine(ALICE);

        assertThat(res.getData()).hasSize(2);
        verify(repo, never()).findAll();
    }

    @Test
    void getMineByIdReturnsTheLetterOfTheOwner() {
        when(repo.findByIdAndUserEmail(1L, ALICE)).thenReturn(Optional.of(lettre(1, ALICE)));

        ApiResponse<LettreResponse> res = service.getMineById(ALICE, 1L);

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getData().getId()).isEqualTo(1L);
    }

    @Test
    void anotherUserCannotReadSomeoneElsesLetter() {
        when(repo.findByIdAndUserEmail(1L, BOB)).thenReturn(Optional.empty());

        ApiResponse<LettreResponse> res = service.getMineById(BOB, 1L);

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("Lettre non trouvée");
        assertThat(res.getData()).isNull();
    }

    // ───────────────────────── Modification ─────────────────────────

    @Test
    void updateChangesTheFieldsAndStampsTheDate() {
        Lettre existing = lettre(1, ALICE);
        when(repo.findByIdAndUserEmail(1L, ALICE)).thenReturn(Optional.of(existing));

        ApiResponse<LettreResponse> res = service.update(ALICE, 1L, request(" Nouvelle SA ", "Chef de projet"));

        assertThat(res.isSuccess()).isTrue();
        assertThat(existing.getNomEntreprise()).isEqualTo("Nouvelle SA");
        assertThat(existing.getPoste()).isEqualTo("Chef de projet");
        assertThat(existing.getUpdatedAt()).isNotNull();
        verify(repo).save(existing);
    }

    @Test
    void anotherUserCannotUpdateSomeoneElsesLetter() {
        when(repo.findByIdAndUserEmail(1L, BOB)).thenReturn(Optional.empty());

        ApiResponse<LettreResponse> res = service.update(BOB, 1L, request("Pirate SA", null));

        assertThat(res.isSuccess()).isFalse();
        verify(repo, never()).save(any());
    }

    // ───────────────────────── Suppression ─────────────────────────

    @Test
    void deleteRemovesTheOwnersLetter() {
        Lettre existing = lettre(1, ALICE);
        when(repo.findByIdAndUserEmail(1L, ALICE)).thenReturn(Optional.of(existing));

        ApiResponse<Void> res = service.delete(ALICE, 1L);

        assertThat(res.isSuccess()).isTrue();
        verify(repo).delete(existing);
    }

    @Test
    void anotherUserCannotDeleteSomeoneElsesLetter() {
        when(repo.findByIdAndUserEmail(1L, BOB)).thenReturn(Optional.empty());

        ApiResponse<Void> res = service.delete(BOB, 1L);

        assertThat(res.isSuccess()).isFalse();
        verify(repo, never()).delete(any());
    }

    // ───────────────────────── Administration ─────────────────────────

    @Test
    void adminGetAllReturnsEveryUsersLetters() {
        when(repo.findAll()).thenReturn(List.of(lettre(1, ALICE), lettre(2, BOB)));

        assertThat(service.getAll().getData()).hasSize(2);
    }

    @Test
    void adminDeleteOfAnUnknownLetterIsAnError() {
        when(repo.existsById(99L)).thenReturn(false);

        ApiResponse<Void> res = service.deleteById(99L);

        assertThat(res.isSuccess()).isFalse();
        verify(repo, never()).deleteById(any());
    }

    @Test
    void adminDeleteOfAnExistingLetterWorks() {
        when(repo.existsById(2L)).thenReturn(true);

        ApiResponse<Void> res = service.deleteById(2L);

        assertThat(res.isSuccess()).isTrue();
        verify(repo).deleteById(2L);
    }
}
