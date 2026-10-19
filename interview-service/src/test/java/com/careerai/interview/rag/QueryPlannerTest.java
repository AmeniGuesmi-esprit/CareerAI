package com.careerai.interview.rag;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QueryPlannerTest {

    private final QueryPlanner planner = new QueryPlanner();
    private static final Set<String> VOCAB = Set.of("java", "spring boot", "jpa", "angular", "rxjs", "docker", "kafka");

    @Test
    void routeLesOffresJavaVersLaPisteBackend() {
        var plan = planner.plan("Développeur Java Spring Boot",
                "Microservices avec Spring Boot, JPA et Docker. 3 ans d'expérience.", "Stage en Java et Spring", VOCAB);
        assertThat(plan.track()).isEqualTo("backend-java-spring");
    }

    @Test
    void routeLesOffresAngularVersLaPisteFrontend() {
        var plan = planner.plan("Dev Angular", "Application Angular avec RxJS et TypeScript, composants et signals.",
                "Projet Angular", VOCAB);
        assertThat(plan.track()).isEqualTo("frontend-angular");
    }

    @Test
    void sansSignalTombeSurLaPisteParDefaut() {
        assertThat(planner.plan("Poste", "Description générique sans technologie particulière pour le test.", "", VOCAB).track())
                .isEqualTo("backend-java-spring");
    }

    @Test
    void deduitLeNiveauDepuisLaCandidatureOuLesAnnees() {
        assertThat(QueryPlanner.inferLevel("stage de fin d'études java")).isEqualTo("junior");
        assertThat(QueryPlanner.inferLevel("profil senior avec forte autonomie")).isEqualTo("senior");
        assertThat(QueryPlanner.inferLevel("minimum 6 ans d'expérience")).isEqualTo("senior");
        assertThat(QueryPlanner.inferLevel("3 ans d'expérience en java")).isEqualTo("confirme");
        assertThat(QueryPlanner.inferLevel("1 an d'expérience")).isEqualTo("junior");
    }

    @Test
    void detecteLesCompetencesCommunesAuCvEtALOffre() {
        var plan = planner.plan("Dev", "Maîtrise de Java et de Docker. Expérience JPA.",
                "Java, JPA, Spring Boot.", VOCAB);
        assertThat(plan.matchedSkills()).containsExactlyInAnyOrder("java", "jpa");
        assertThat(plan.gapSkills()).containsExactly("docker");
    }

    @Test
    void lesLacunesSontLesCompetencesDeLOffreAbsentesDuCv() {
        var plan = planner.plan("Dev", "Il faut Java, Docker et Kafka pour ce poste de développeur backend.",
                "Java uniquement, expérience backend.", VOCAB);
        assertThat(plan.gapSkills()).containsExactlyInAnyOrder("docker", "kafka");
        assertThat(plan.matchedSkills()).containsExactly("java");
        assertThat(plan.queries()).hasSize(3);
    }

    @Test
    void unTermeNeMatchePasDansUnAutreMot() {
        assertThat(QueryPlanner.contains("javascript", "java")).isFalse();
        assertThat(QueryPlanner.contains("c'est du java.", "java")).isTrue();
    }
}
