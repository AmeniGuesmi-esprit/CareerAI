package com.careerai.interview.rag;

import com.careerai.interview.rag.KnowledgeParser.KnowledgeChunk;
import com.careerai.interview.rag.KnowledgeParser.KnowledgeDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeParserTest {

    private final KnowledgeParser parser = new KnowledgeParser();

    private static final String HEADER = """
            ---
            id: test-doc
            title: Test
            track: backend-java-spring
            doc_type: question_bank
            lang: fr
            version: 1
            ---

            # Test

            ## Section A

            ### Question une
            - **Catégorie** : technique
            - **Niveau** : junior
            - **Tags** : java, spring
            - **Intention** : tester.
            - **Points attendus** : a ; b ; c
            - **Réponse modèle (plan)** : plan.
            - **Pièges** : piège.
            """;

    @Test
    void parseLaBaseDeConnaissancesVersionneeSansErreur() throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(Path.of("../knowledge"))) {
            files = walk.filter(p -> p.toString().endsWith(".md")).toList();
        }
        List<KnowledgeDocument> docs = files.stream().map(parser::parse).toList();
        assertThat(docs).hasSize(5);
        long questions = docs.stream().filter(d -> d.docType().equals("question_bank"))
                .mapToLong(d -> d.chunks().size()).sum();
        assertThat(questions).isGreaterThanOrEqualTo(80);
        assertThat(docs.stream().flatMap(d -> d.chunks().stream()).map(KnowledgeChunk::citationId).distinct().count())
                .isEqualTo(docs.stream().mapToLong(d -> d.chunks().size()).sum());
    }

    @Test
    void chunkIdsSontDeterministes(@TempDir Path dir) throws IOException {
        Path file = write(dir, "a.md", HEADER);
        assertThat(parser.parse(file).chunks()).isEqualTo(parser.parse(file).chunks());
        KnowledgeChunk chunk = parser.parse(file).chunks().get(0);
        assertThat(chunk.citationId()).isEqualTo("test-doc/question-une");
        assertThat(chunk.indexText()).startsWith("[backend-java-spring | junior | Section A] Question une");
        assertThat(chunk.tags()).containsExactly("java", "spring");
    }

    @Test
    void refuseUnNiveauInvalide(@TempDir Path dir) throws IOException {
        Path file = write(dir, "b.md", HEADER.replace("junior", "expert"));
        assertThatThrownBy(() -> parser.parse(file)).hasMessageContaining("niveau invalide");
    }

    @Test
    void refuseUnTitreEnDouble(@TempDir Path dir) throws IOException {
        String dup = HEADER + "\n### Question une\n- **Catégorie** : technique\n- **Niveau** : junior\n"
                + "- **Tags** : java\n- **Intention** : x\n- **Points attendus** : y\n"
                + "- **Réponse modèle (plan)** : z\n- **Pièges** : w\n";
        Path file = write(dir, "c.md", dup);
        assertThatThrownBy(() -> parser.parse(file)).hasMessageContaining("en double");
    }

    @Test
    void refuseUnChampObligatoireManquant(@TempDir Path dir) throws IOException {
        Path file = write(dir, "d.md", HEADER.replace("- **Pièges** : piège.\n", ""));
        assertThatThrownBy(() -> parser.parse(file)).hasMessageContaining("pieges");
    }

    @Test
    void refuseUnTrackInconnu(@TempDir Path dir) throws IOException {
        Path file = write(dir, "e.md", HEADER.replace("track: backend-java-spring", "track: cobol"));
        assertThatThrownBy(() -> parser.parse(file)).hasMessageContaining("track invalide");
    }

    @Test
    void decoupeUnItemTropLongEnGardantUnChevauchement(@TempDir Path dir) throws IOException {
        StringBuilder long1 = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            long1.append("- **Point ").append(i).append("** : ").append("contenu répété ".repeat(4)).append('\n');
        }
        String body = HEADER.replace("- **Pièges** : piège.\n", "") + long1;
        Path file = write(dir, "f.md", body + "- **Pièges** : piège.\n");
        List<KnowledgeChunk> chunks = parser.parse(file).chunks();
        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.body().length()).isLessThanOrEqualTo(KnowledgeParser.MAX_CHUNK_CHARS + 200));
        assertThat(chunks.get(1).body()).contains(chunks.get(0).body().lines().reduce((a, b) -> b).orElse(""));
    }

    private static Path write(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
