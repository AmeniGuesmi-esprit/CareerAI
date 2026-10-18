package com.careerai.experience.dto;

import com.careerai.experience.model.Experience;
import com.careerai.experience.model.TypeContrat;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DtoTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    // =====================================================================
    // ExperienceRequest (Bean Validation)
    // =====================================================================

    @Nested
    @DisplayName("ExperienceRequest : validation")
    class ExperienceRequestValidation {

        private ExperienceRequest valid() {
            ExperienceRequest r = new ExperienceRequest();
            r.setPoste("Developpeur");
            r.setEntreprise("Acme");
            r.setLieu("Tunis");
            r.setTypeContrat(TypeContrat.CDI);
            r.setDateDebut(LocalDate.of(2024, 1, 1));
            r.setDateFin(LocalDate.of(2024, 12, 31));
            r.setDescription("Une description");
            return r;
        }

        private Set<String> messages(ExperienceRequest r) {
            return validator.validate(r).stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Test
        @DisplayName("une requete complete est valide")
        void validRequest() {
            assertThat(validator.validate(valid())).isEmpty();
        }

        @Test
        @DisplayName("lieu, description et dateFin sont facultatifs")
        void optionalFields() {
            ExperienceRequest r = valid();
            r.setLieu(null);
            r.setDescription(null);
            r.setDateFin(null);

            assertThat(validator.validate(r)).isEmpty();
        }

        @Test
        @DisplayName("poste vide, null ou blanc -> invalide")
        void blankPoste() {
            ExperienceRequest r = valid();
            for (String poste : new String[]{null, "", "   "}) {
                r.setPoste(poste);
                assertThat(messages(r)).contains("Le poste est obligatoire");
            }
        }

        @Test
        @DisplayName("entreprise blanche -> invalide")
        void blankEntreprise() {
            ExperienceRequest r = valid();
            r.setEntreprise(" ");

            assertThat(messages(r)).containsExactly("L'entreprise est obligatoire");
        }

        @Test
        @DisplayName("poste de 151 caracteres -> invalide, 150 -> valide")
        void posteMaxLength() {
            ExperienceRequest r = valid();
            r.setPoste("a".repeat(150));
            assertThat(validator.validate(r)).isEmpty();

            r.setPoste("a".repeat(151));
            assertThat(messages(r)).containsExactly("Le poste ne doit pas depasser 150 caracteres");
        }

        @Test
        @DisplayName("description de 2001 caracteres -> invalide, 2000 -> valide")
        void descriptionMaxLength() {
            ExperienceRequest r = valid();
            r.setDescription("a".repeat(2000));
            assertThat(validator.validate(r)).isEmpty();

            r.setDescription("a".repeat(2001));
            assertThat(messages(r)).containsExactly("La description ne doit pas depasser 2000 caracteres");
        }

        @Test
        @DisplayName("type de contrat et date de debut obligatoires")
        void requiredFields() {
            ExperienceRequest r = valid();
            r.setTypeContrat(null);
            r.setDateDebut(null);

            assertThat(messages(r)).containsExactlyInAnyOrder(
                    "Le type de contrat est obligatoire",
                    "La date de debut est obligatoire");
        }
    }

    // =====================================================================
    // PitchRequest (Bean Validation)
    // =====================================================================

    @Nested
    @DisplayName("PitchRequest : validation")
    class PitchRequestValidation {

        private Set<String> messages(String text) {
            PitchRequest r = new PitchRequest();
            r.setExperiences(text);
            return validator.validate(r).stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Test
        @DisplayName("texte de 50 a 5000 caracteres -> valide")
        void validBounds() {
            assertThat(messages("a".repeat(50))).isEmpty();
            assertThat(messages("a".repeat(5000))).isEmpty();
        }

        @Test
        @DisplayName("49 ou 5001 caracteres -> invalide")
        void invalidBounds() {
            assertThat(messages("a".repeat(49))).containsExactly(
                    "Le texte des experiences doit contenir entre 50 et 5000 caracteres");
            assertThat(messages("a".repeat(5001))).containsExactly(
                    "Le texte des experiences doit contenir entre 50 et 5000 caracteres");
        }

        @Test
        @DisplayName("texte null -> invalide (obligatoire)")
        void nullText() {
            assertThat(messages(null)).contains("Le texte des experiences est obligatoire");
        }
    }

    // =====================================================================
    // ExperienceResponse.from
    // =====================================================================

    @Nested
    @DisplayName("ExperienceResponse.from")
    class ExperienceResponseMapping {

        @Test
        @DisplayName("copie tous les champs de l'entite")
        void mapsAllFields() {
            LocalDateTime created = LocalDateTime.of(2024, 1, 1, 10, 0);
            LocalDateTime updated = LocalDateTime.of(2024, 2, 1, 10, 0);
            Experience entity = Experience.builder()
                    .id(1L).userId(7L)
                    .poste("Developpeur").entreprise("Acme").lieu("Tunis")
                    .typeContrat(TypeContrat.CDD)
                    .dateDebut(LocalDate.of(2023, 1, 1)).dateFin(LocalDate.of(2023, 12, 31))
                    .enCours(false).description("Desc")
                    .createdAt(created).updatedAt(updated)
                    .build();

            ExperienceResponse dto = ExperienceResponse.from(entity);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getPoste()).isEqualTo("Developpeur");
            assertThat(dto.getEntreprise()).isEqualTo("Acme");
            assertThat(dto.getLieu()).isEqualTo("Tunis");
            assertThat(dto.getTypeContrat()).isEqualTo(TypeContrat.CDD);
            assertThat(dto.getDateDebut()).isEqualTo(LocalDate.of(2023, 1, 1));
            assertThat(dto.getDateFin()).isEqualTo(LocalDate.of(2023, 12, 31));
            assertThat(dto.isEnCours()).isFalse();
            assertThat(dto.getDescription()).isEqualTo("Desc");
            assertThat(dto.getCreatedAt()).isEqualTo(created);
            assertThat(dto.getUpdatedAt()).isEqualTo(updated);
        }

        @Test
        @DisplayName("n'expose pas l'userId du proprietaire")
        void doesNotExposeUserId() {
            assertThat(ExperienceResponse.class.getDeclaredFields())
                    .extracting(java.lang.reflect.Field::getName)
                    .doesNotContain("userId");
        }
    }

    // =====================================================================
    // ApiResponse
    // =====================================================================

    @Nested
    @DisplayName("ApiResponse")
    class ApiResponseFactories {

        @Test
        @DisplayName("success() : success = true, message et data renseignes")
        void success() {
            ApiResponse<String> r = ApiResponse.success("OK", "donnees");

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getMessage()).isEqualTo("OK");
            assertThat(r.getData()).isEqualTo("donnees");
        }

        @Test
        @DisplayName("error() : success = false, data = null")
        void error() {
            ApiResponse<Void> r = ApiResponse.error("Erreur");

            assertThat(r.isSuccess()).isFalse();
            assertThat(r.getMessage()).isEqualTo("Erreur");
            assertThat(r.getData()).isNull();
        }
    }
}
