package com.careerai.compatibility_service.service;

import com.careerai.compatibility_service.service.SkillReferential.LookupResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SkillReferentialTest {

    private static SkillReferential referential;

    @BeforeAll
    static void load() throws Exception {
        referential = new SkillReferential(new ObjectMapper());
    }

    private static LookupResult one(String term) {
        List<LookupResult> results = referential.lookup(List.of(term));
        assertThat(results).hasSize(1);
        return results.get(0);
    }

    @Test
    void trouveUneCompetenceParSonNom() {
        LookupResult r = one("Java");

        assertThat(r.trouve()).isTrue();
        assertThat(r.competence()).isEqualTo("Java");
        assertThat(r.famille()).isEqualTo("Langages");
        assertThat(r.equivalents()).containsExactly("Java", "J2EE", "Java EE", "Jakarta EE", "JDK");
        assertThat(r.proches()).containsExactly("Spring Boot", "Kotlin", "Maven");
    }

    @Test
    void reconnaitUnSynonyme() {
        LookupResult r = one("MySQL");

        assertThat(r.terme()).isEqualTo("MySQL");
        assertThat(r.competence()).isEqualTo("SQL");
        assertThat(r.famille()).isEqualTo("Bases de données");
    }

    @Test
    void ignoreLaCasseLesTiretsEtLesEspaces() {
        LookupResult r = one("  spring-BOOT ");

        assertThat(r.terme()).isEqualTo("spring-BOOT");
        assertThat(r.competence()).isEqualTo("Spring Boot");
    }

    @Test
    void ignoreLesAccents() {
        assertThat(one("securite applicative").competence()).isEqualTo("Sécurité applicative");
        assertThat(one("Integration continue").competence()).isEqualTo("CI/CD");
    }

    @Test
    void renvoieNonTrouvePourUnTermeInconnu() {
        LookupResult r = one("Cobol");

        assertThat(r).isEqualTo(new LookupResult("Cobol", false, null, null, List.of(), List.of()));
    }

    @Test
    void filtreLesTermesNullsVidesEtEnDouble() {
        List<LookupResult> results = referential.lookup(Arrays.asList("Java", null, "  ", "Java", " Java "));

        assertThat(results).extracting(LookupResult::terme).containsExactly("Java");
    }

    @Test
    void limiteLaRechercheAVingtTermes() {
        List<String> terms = IntStream.range(0, 25).mapToObj(i -> "terme" + i).toList();

        assertThat(referential.lookup(terms)).hasSize(SkillReferential.MAX_TERMS).hasSize(20);
    }

    @Test
    void renvoieUneListeVidePourUneEntreeNulle() {
        assertThat(referential.lookup(null)).isEmpty();
    }

    @Test
    void normaliseCasseAccentsTiretsEtEspaces() {
        assertThat(SkillReferential.normalize("  Spring-Boot  ")).isEqualTo("spring boot");
        assertThat(SkillReferential.normalize("Déploiement   Continu")).isEqualTo("deploiement continu");
    }
}
