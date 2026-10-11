package com.careerai.interview.rag;

import com.careerai.interview.ai.OllamaClient;
import com.careerai.interview.config.InterviewConfig.RagProperties;
import com.careerai.interview.exception.ApiException;
import com.careerai.interview.rag.KnowledgeParser.KnowledgeChunk;
import com.careerai.interview.rag.KnowledgeParser.KnowledgeDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagIndexer {

    public record Stats(int documents, int chunks, int reusedEmbeddings, int newEmbeddings, int removedChunks,
                        String mode, String corpusHash, String embedModel, Integer dims) { }

    private static final int MANIFEST_ID = 1;

    private final KnowledgeParser parser;
    private final OllamaClient ollama;
    private final RagChunkRepository chunks;
    private final RagManifestRepository manifests;
    private final RagIndex index;
    private final RagProperties rag;

    public synchronized Stats reindex() {
        List<KnowledgeDocument> docs = loadDocuments();
        List<KnowledgeChunk> all = docs.stream().flatMap(d -> d.chunks().stream()).toList();
        Map<String, RagChunk> existing = new HashMap<>();
        chunks.findAll().forEach(c -> existing.put(c.getChunkId(), c));

        String model = ollama.embedModel();
        Map<String, RagChunk> next = new HashMap<>();
        List<KnowledgeChunk> needEmbedding = new ArrayList<>();
        int reused = 0;
        for (KnowledgeChunk c : all) {
            RagChunk old = existing.get(c.chunkId());
            boolean cached = old != null && old.getEmbedding() != null && model.equals(old.getEmbedModel())
                    && c.contentHash().equals(old.getContentHash());
            next.put(c.chunkId(), toEntity(c, cached ? old.getEmbedding() : null, cached ? model : null));
            if (cached) {
                reused++;
            } else {
                needEmbedding.add(c);
            }
        }

        int embedded = 0;
        if (!needEmbedding.isEmpty()) {
            try {
                List<float[]> vectors = ollama.embed(needEmbedding.stream().map(KnowledgeChunk::indexText).toList());
                for (int i = 0; i < vectors.size(); i++) {
                    RagChunk entity = next.get(needEmbedding.get(i).chunkId());
                    entity.setEmbedding(RagIndex.encode(vectors.get(i)));
                    entity.setEmbedModel(model);
                }
                embedded = vectors.size();
            } catch (ApiException e) {
                log.warn("Embeddings non calculés ({}) : index BM25 seul. Relancez le réindex quand Ollama est disponible.",
                        e.getCode());
            }
        }

        boolean complete = next.values().stream().allMatch(c -> c.getEmbedding() != null);
        Integer dims = next.values().stream().filter(c -> c.getEmbedding() != null).findFirst()
                .map(c -> c.getEmbedding().length / Float.BYTES).orElse(null);
        String mode = complete && !next.isEmpty() ? "hybrid" : "bm25";

        Set<String> removed = new HashSet<>(existing.keySet());
        removed.removeAll(next.keySet());
        chunks.saveAll(next.values());
        if (!removed.isEmpty()) {
            chunks.deleteAllById(removed);
        }

        String corpusHash = KnowledgeParser.sha256(all.stream()
                .map(c -> c.chunkId() + ":" + c.contentHash())
                .sorted()
                .reduce("", (a, b) -> a + b + "\n")
                .getBytes(StandardCharsets.UTF_8));
        String digest = digestOf(model);
        manifests.save(RagManifest.builder()
                .id(MANIFEST_ID)
                .corpusHash(corpusHash)
                .chatModel(ollama.chatModel())
                .embedModel(model)
                .embedDigest(digest)
                .dims(dims)
                .chunkCount(all.size())
                .builtAt(LocalDateTime.now())
                .build());
        index.reload();
        return new Stats(docs.size(), all.size(), reused, embedded, removed.size(), mode, corpusHash, model, dims);
    }

    private String digestOf(String model) {
        try {
            return ollama.tags().stream().filter(m -> m.name().equals(model) || m.name().equals(model + ":latest"))
                    .map(OllamaClient.ModelInfo::digest).findFirst().orElse(null);
        } catch (ApiException e) {
            return null;
        }
    }

    private List<KnowledgeDocument> loadDocuments() {
        Path root = Path.of(rag.knowledgePath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "KB_NOT_FOUND",
                    "Dossier de connaissances introuvable : " + root);
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> p.toString().endsWith(".md") && Files.isRegularFile(p))
                    .sorted(Comparator.comparing(Path::toString)).toList();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "KB_NOT_FOUND", "Lecture du dossier impossible : " + root);
        }
        if (files.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "KB_EMPTY", "Aucun fichier .md dans " + root);
        }
        List<KnowledgeDocument> docs = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Path f : files) {
            try {
                KnowledgeDocument d = parser.parse(f);
                if (!ids.add(d.docId())) {
                    throw new IllegalStateException("id de document en double : " + d.docId());
                }
                docs.add(d);
            } catch (IllegalStateException e) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "KB_INVALID", e.getMessage());
            }
        }
        return docs;
    }

    private static RagChunk toEntity(KnowledgeChunk c, byte[] embedding, String embedModel) {
        return RagChunk.builder()
                .chunkId(c.chunkId())
                .docId(c.docId())
                .citationId(c.citationId())
                .track(c.track())
                .docType(c.docType())
                .niveau(c.niveau())
                .section(c.section())
                .title(c.title())
                .tags(String.join(",", c.tags()))
                .body(c.body())
                .indexText(c.indexText())
                .contentHash(c.contentHash())
                .embedModel(embedModel)
                .embedding(embedding)
                .build();
    }
}
