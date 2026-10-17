package com.careerai.skillgap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Garde-fous sur les fichiers knowledge/*.json : un fichier mal formé casserait le RAG au démarrage. */
class KnowledgeBaseTest {

    /** 5 fiches (rag.top-k) doivent tenir dans rag.context-max-chars = 1800. */
    private static final int MAX_CONTENT_CHARS = 320;

    private static List<JsonNode> docs;

    @BeforeAll
    static void load() throws Exception {
        docs = RagService.readKnowledgeBase(new ObjectMapper());
    }

    @Test
    void everyDomainFileIsLoaded() {
        Set<String> categories = new HashSet<>();
        docs.forEach(d -> categories.add(d.path("category").asText()));
        assertThat(docs).hasSizeGreaterThan(100);
        assertThat(categories).contains("DevOps", "Marketing digital", "Finance", "Santé", "Ingénierie",
                "Ressources humaines", "Juridique", "Langues", "Compétences transversales");
    }

    @Test
    void everyFicheHasTheRequiredFields() {
        for (JsonNode d : docs) {
            for (String field : List.of("slug", "skill", "category", "content")) {
                assertThat(d.path(field).asText()).as(field + " de " + d.path("slug").asText()).isNotBlank();
            }
        }
    }

    @Test
    void slugsAreUniqueAcrossFiles() {
        Set<String> seen = new HashSet<>();
        for (JsonNode d : docs) {
            assertThat(seen.add(d.path("slug").asText())).as("slug en double : " + d.path("slug").asText()).isTrue();
        }
    }

    @Test
    void contentFitsInThePromptContext() {
        for (JsonNode d : docs) {
            assertThat(d.path("content").asText().length()).as(d.path("slug").asText())
                    .isLessThanOrEqualTo(MAX_CONTENT_CHARS);
        }
    }

    @Test
    void slugsAreUrlSafe() {
        docs.forEach(d -> assertThat(d.path("slug").asText()).matches("[a-z0-9-]+"));
    }
}
