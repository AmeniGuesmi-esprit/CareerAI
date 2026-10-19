package com.careerai.interview.rag;

import com.careerai.interview.ai.OllamaClient;
import com.careerai.interview.config.InterviewConfig.RagProperties;
import com.careerai.interview.rag.KnowledgeParser.KnowledgeChunk;
import com.careerai.interview.rag.KnowledgeParser.KnowledgeDocument;
import com.careerai.interview.rag.RagRetriever.Filter;
import com.careerai.interview.rag.RagRetriever.Hit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagEvaluationTest {

    private static RagRetriever retriever;
    private static final Map<String, String> TITLE_TO_CITATION = new LinkedHashMap<>();

    @BeforeAll
    static void indexKnowledgeBase() throws IOException {
        KnowledgeParser parser = new KnowledgeParser();
        List<KnowledgeDocument> docs;
        try (Stream<Path> walk = Files.walk(Path.of("../knowledge"))) {
            docs = walk.filter(p -> p.toString().endsWith(".md")).sorted().map(parser::parse).toList();
        }
        List<RagChunk> rows = new ArrayList<>();
        for (KnowledgeDocument d : docs) {
            for (KnowledgeChunk c : d.chunks()) {
                TITLE_TO_CITATION.put(c.title(), c.citationId());
                rows.add(RagChunk.builder().chunkId(c.chunkId()).docId(c.docId()).citationId(c.citationId())
                        .track(c.track()).docType(c.docType()).niveau(c.niveau()).section(c.section()).title(c.title())
                        .tags(String.join(",", c.tags())).body(c.body()).indexText(c.indexText())
                        .contentHash(c.contentHash()).build());
            }
        }
        RagChunkRepository repo = mock(RagChunkRepository.class);
        when(repo.findAll()).thenReturn(rows);
        RagIndex index = new RagIndex(repo);
        index.reload();
        retriever = new RagRetriever(index, mock(OllamaClient.class),
                new RagProperties("../knowledge", "rag-test", 6, 4, 60, 0.7, 32));
    }

    @Test
    void rechercheLexicaleDesItemsAttendusSurLeJeuDeReference() throws IOException {
        List<Map<String, Object>> gold;
        try (InputStream in = getClass().getResourceAsStream("/rag-eval/gold.yml")) {
            gold = new Yaml().load(in);
        }
        int hitsAt5 = 0;
        double reciprocal = 0;
        List<String> failures = new ArrayList<>();
        StringBuilder report = new StringBuilder("# Évaluation du retrieval (BM25, index sans embeddings)\n\n");
        report.append("| Requête | Attendu | Rang | Top 1 |\n|---|---|---|---|\n");
        for (Map<String, Object> q : gold) {
            String query = (String) q.get("query");
            String track = (String) q.get("track");
            Set<String> docTypes = q.get("docTypes") == null ? null : Set.copyOf((List<String>) q.get("docTypes"));
            List<String> expected = ((List<String>) q.get("expected")).stream()
                    .map(TITLE_TO_CITATION::get).toList();
            List<Hit> hits = retriever.retrieve(List.of(query), new Filter(track, docTypes, null), 10).hits();
            int rank = -1;
            for (int i = 0; i < hits.size(); i++) {
                if (expected.contains(hits.get(i).entry().citationId())) {
                    rank = i + 1;
                    break;
                }
            }
            if (rank >= 1 && rank <= 5) {
                hitsAt5++;
            } else {
                failures.add(query);
            }
            if (rank >= 1 && rank <= 10) {
                reciprocal += 1.0 / rank;
            }
            String top = hits.isEmpty() ? "-" : hits.get(0).entry().title();
            report.append("| ").append(query).append(" | ").append(expected.get(0)).append(" | ")
                    .append(rank < 0 ? "absent" : String.valueOf(rank)).append(" | ").append(top).append(" |\n");
        }
        double recallAt5 = (double) hitsAt5 / gold.size();
        double mrrAt10 = reciprocal / gold.size();
        report.insert(0, String.format("Recall@5 = %.2f  MRR@10 = %.2f  (n = %d)%n%n", recallAt5, mrrAt10, gold.size()));
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/rag-eval-report.md"), report.toString());
        assertThat(recallAt5).as("Recall@5 (voir target/rag-eval-report.md) hors requêtes : %s", failures)
                .isGreaterThanOrEqualTo(0.8);
    }
}
