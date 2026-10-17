package com.careerai.experience.repository;

import com.careerai.experience.model.Experience;
import com.careerai.experience.model.TypeContrat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de la couche JPA sur une base H2 en memoire (remplace MySQL pendant le test uniquement).
 * Necessite la dependance de test com.h2database:h2.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ExperienceRepositoryTest {

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;

    @Autowired
    private ExperienceRepository repository;

    private Experience oldest;
    private Experience newest;
    private Experience middle;
    private Experience otherUsers;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        oldest = repository.save(experience(USER_A, "Stagiaire", LocalDate.of(2020, 1, 1)));
        newest = repository.save(experience(USER_A, "Ingenieur", LocalDate.of(2024, 1, 1)));
        middle = repository.save(experience(USER_A, "Developpeur", LocalDate.of(2022, 1, 1)));
        otherUsers = repository.save(experience(USER_B, "Consultant", LocalDate.of(2023, 1, 1)));
    }

    private static Experience experience(Long userId, String poste, LocalDate debut) {
        return Experience.builder()
                .userId(userId)
                .poste(poste)
                .entreprise("Acme")
                .typeContrat(TypeContrat.CDI)
                .dateDebut(debut)
                .build();
    }

    @Test
    @DisplayName("findByUserIdOrderByDateDebutDesc : uniquement les experiences de l'utilisateur, plus recentes d'abord")
    void findByUserId_returnsOwnExperiencesSortedByStartDateDesc() {
        List<Experience> result = repository.findByUserIdOrderByDateDebutDesc(USER_A);

        assertThat(result).extracting(Experience::getPoste)
                .containsExactly("Ingenieur", "Developpeur", "Stagiaire");
        assertThat(result).doesNotContain(otherUsers);
    }

    @Test
    @DisplayName("findByUserIdOrderByDateDebutDesc : liste vide pour un utilisateur sans experience")
    void findByUserId_unknownUser_returnsEmpty() {
        assertThat(repository.findByUserIdOrderByDateDebutDesc(999L)).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndUserId : retrouve l'experience de son proprietaire")
    void findByIdAndUserId_owner_found() {
        Optional<Experience> result = repository.findByIdAndUserId(newest.getId(), USER_A);

        assertThat(result).isPresent();
        assertThat(result.get().getPoste()).isEqualTo("Ingenieur");
    }

    @Test
    @DisplayName("findByIdAndUserId : vide si l'experience appartient a un autre utilisateur")
    void findByIdAndUserId_otherUser_empty() {
        assertThat(repository.findByIdAndUserId(newest.getId(), USER_B)).isEmpty();
        assertThat(repository.findByIdAndUserId(otherUsers.getId(), USER_A)).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndUserId : vide si l'id n'existe pas")
    void findByIdAndUserId_unknownId_empty() {
        assertThat(repository.findByIdAndUserId(123456L, USER_A)).isEmpty();
    }

    @Test
    @DisplayName("save : genere un id et initialise createdAt / enCours par defaut")
    void save_setsGeneratedIdAndDefaults() {
        assertThat(middle.getId()).isNotNull();
        assertThat(middle.getCreatedAt()).isNotNull();
        assertThat(middle.isEnCours()).isFalse();
    }
}
