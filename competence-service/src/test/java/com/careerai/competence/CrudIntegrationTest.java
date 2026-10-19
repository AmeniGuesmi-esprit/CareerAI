package com.careerai.competence;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CrudIntegrationTest {

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secret;

    private String token(long userId) {
        return "Bearer " + Jwts.builder()
                .setClaims(Map.of("userId", userId, "role", "USER"))
                .setSubject("u" + userId + "@test.com")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret)), SignatureAlgorithm.HS256)
                .compact();
    }

    private static final String FORMATION = """
            {"diplome":"Licence SAE","etablissement":"ESPRIT","domaine":"Informatique",
             "dateDebut":"2023-09-01","dateFin":"2026-06-30","enCours":false,"description":"x"}""";
    private static final String COMPETENCE = """
            {"nom":"Spring Boot","niveau":"AVANCE","categorie":"TECHNIQUE"}""";

    @Test
    void unauthenticatedIs401() throws Exception {
        mvc.perform(get("/api/formations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/competences").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
    }

    @Test
    void formationCrudAndOwnership() throws Exception {
        MvcResult r = mvc.perform(post("/api/formations").header("Authorization", token(1))
                        .contentType(MediaType.APPLICATION_JSON).content(FORMATION))
                .andExpect(status().isCreated()).andReturn();
        String body = r.getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(get("/api/formations").header("Authorization", token(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        // autre utilisateur : ne voit rien et ne peut ni lire, ni modifier, ni supprimer
        mvc.perform(get("/api/formations").header("Authorization", token(2)))
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/formations/" + id).header("Authorization", token(2))).andExpect(status().isNotFound());
        mvc.perform(put("/api/formations/" + id).header("Authorization", token(2))
                .contentType(MediaType.APPLICATION_JSON).content(FORMATION)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/formations/" + id).header("Authorization", token(2))).andExpect(status().isNotFound());

        mvc.perform(put("/api/formations/" + id).header("Authorization", token(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FORMATION.replace("Licence SAE", "Ingénieur")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.diplome").value("Ingénieur"));
        mvc.perform(delete("/api/formations/" + id).header("Authorization", token(1))).andExpect(status().isOk());
        mvc.perform(get("/api/formations/" + id).header("Authorization", token(1))).andExpect(status().isNotFound());
    }

    @Test
    void formationValidation() throws Exception {
        mvc.perform(post("/api/formations").header("Authorization", token(1))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/formations").header("Authorization", token(1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(FORMATION.replace("2026-06-30", "2020-01-01"))).andExpect(status().isBadRequest());
    }

    @Test
    void competenceCrudAndDuplicate() throws Exception {
        MvcResult r = mvc.perform(post("/api/competences").header("Authorization", token(3))
                        .contentType(MediaType.APPLICATION_JSON).content(COMPETENCE))
                .andExpect(status().isCreated()).andReturn();
        String id = r.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");
        assertThat(id).matches("\\d+");

        mvc.perform(post("/api/competences").header("Authorization", token(3))
                .contentType(MediaType.APPLICATION_JSON).content(COMPETENCE.replace("Spring Boot", "spring boot")))
                .andExpect(status().isConflict());
        // un autre utilisateur peut avoir la même compétence
        mvc.perform(post("/api/competences").header("Authorization", token(4))
                .contentType(MediaType.APPLICATION_JSON).content(COMPETENCE)).andExpect(status().isCreated());

        mvc.perform(put("/api/competences/" + id).header("Authorization", token(3))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPETENCE.replace("AVANCE", "EXPERT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.niveau").value("EXPERT"));
        mvc.perform(post("/api/competences").header("Authorization", token(3))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"X\",\"niveau\":\"NOPE\",\"categorie\":\"TECHNIQUE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/competences/" + id).header("Authorization", token(3))).andExpect(status().isOk());
        mvc.perform(get("/api/competences").header("Authorization", token(3)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
