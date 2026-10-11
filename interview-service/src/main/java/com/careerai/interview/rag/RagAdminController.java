package com.careerai.interview.rag;

import com.careerai.interview.ai.OllamaClient;
import com.careerai.interview.config.InterviewConfig.RagProperties;
import com.careerai.interview.exception.ApiException;
import com.careerai.interview.rag.RagIndex.Entry;
import com.careerai.interview.rag.RagIndex.Snapshot;
import com.careerai.interview.rag.RagRetriever.Filter;
import com.careerai.interview.rag.RagRetriever.Hit;
import com.careerai.interview.rag.RagRetriever.Retrieval;
import com.careerai.interview.security.Access;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/interview/admin/rag")
@RequiredArgsConstructor
public class RagAdminController {

    public record SearchRequest(@NotBlank @Size(max = 1000) String query, String track, Set<String> docTypes, Integer k) { }

    public record HitView(String citationId, String title, String track, String docType, String niveau,
                          String section, double score, Integer denseRank, Integer bm25Rank) { }

    public record SearchResponse(String mode, List<HitView> hits) { }

    public record StatusResponse(int loadedChunks, boolean denseAvailable, int vocabularySize, String chatModel,
                                 String embedModel, boolean ollamaReachable, boolean chatModelPresent,
                                 boolean embedModelPresent, String ollamaError, RagManifest manifest) { }

    private final RagIndexer indexer;
    private final RagRetriever retriever;
    private final RagIndex index;
    private final OllamaClient ollama;
    private final RagManifestRepository manifests;
    private final RagProperties rag;

    @PostMapping("/reindex")
    public RagIndexer.Stats reindex(@RequestHeader(Access.ROLE_HEADER) String role) {
        Access.requireAdmin(role);
        return indexer.reindex();
    }

    @GetMapping("/status")
    public StatusResponse status(@RequestHeader(Access.ROLE_HEADER) String role) {
        Access.requireAdmin(role);
        Snapshot s = index.snapshot();
        boolean reachable = false;
        boolean chatPresent = false;
        boolean embedPresent = false;
        String error = null;
        try {
            List<OllamaClient.ModelInfo> models = ollama.tags();
            reachable = true;
            chatPresent = models.stream().anyMatch(m -> m.name().startsWith(ollama.chatModel()));
            embedPresent = models.stream().anyMatch(m -> m.name().startsWith(ollama.embedModel()));
        } catch (ApiException e) {
            error = e.getCode();
        }
        return new StatusResponse(s.entries().size(), s.dense(), s.vocabulary().size(), ollama.chatModel(),
                ollama.embedModel(), reachable, chatPresent, embedPresent, error,
                manifests.findById(1).orElse(null));
    }

    @PostMapping("/search")
    public SearchResponse search(@RequestHeader(Access.ROLE_HEADER) String role, @RequestBody SearchRequest req) {
        Access.requireAdmin(role);
        String track = req.track() == null ? "backend-java-spring" : req.track();
        int k = req.k() == null ? rag.questionsTopK() : req.k();
        Retrieval r = retriever.retrieve(List.of(req.query()),
                new Filter(track, req.docTypes(), null), k);
        return new SearchResponse(r.mode(), r.hits().stream().map(this::view).toList());
    }

    private HitView view(Hit h) {
        Entry e = h.entry();
        return new HitView(e.citationId(), e.title(), e.track(), e.docType(), e.niveau(), e.section(),
                Math.round(h.score() * 10000) / 10000.0, h.denseRank(), h.bm25Rank());
    }
}
