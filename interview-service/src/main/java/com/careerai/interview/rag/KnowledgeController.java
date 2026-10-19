package com.careerai.interview.rag;

import com.careerai.interview.rag.RagIndex.Entry;
import com.careerai.interview.rag.RagIndex.Snapshot;
import com.careerai.interview.rag.RagRetriever.Filter;
import com.careerai.interview.rag.RagRetriever.Hit;
import com.careerai.interview.rag.RagRetriever.Retrieval;
import com.careerai.interview.security.Access;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/interview/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    public record DocSummary(String docId, String docType, String track, int items, List<String> sections) { }

    public record ManifestView(String corpusHash, String chatModel, String embedModel, Integer dims, int chunkCount,
                               LocalDateTime builtAt) { }

    public record Overview(String mode, int items, Map<String, Integer> itemsByTrack, List<DocSummary> documents,
                           ManifestView manifest) { }

    public record SearchRequest(@NotBlank @Size(max = 500) String query, String track, String docType,
                                @Min(1) @Max(20) Integer k) { }

    public record SearchHit(String citationId, String title, String track, String docType, String niveau,
                            String section, String excerpt, double score, Integer denseRank, Integer bm25Rank) { }

    public record SearchResult(String mode, String track, String query, List<SearchHit> hits) { }

    private final RagIndex index;
    private final RagRetriever retriever;
    private final RagManifestRepository manifests;

    @GetMapping
    public Overview overview(@RequestHeader(Access.USER_HEADER) Long userId) {
        Snapshot s = index.snapshot();
        Map<String, DocAcc> docs = new LinkedHashMap<>();
        Map<String, Set<String>> itemsByTrack = new LinkedHashMap<>();
        Set<String> items = new LinkedHashSet<>();
        for (Entry e : s.entries()) {
            String docId = e.citationId().substring(0, e.citationId().indexOf('/'));
            docs.computeIfAbsent(docId, k -> new DocAcc(e.docType(), e.track())).add(e);
            items.add(e.citationId());
            itemsByTrack.computeIfAbsent(e.track(), k -> new LinkedHashSet<>()).add(e.citationId());
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        itemsByTrack.forEach((k, v) -> counts.put(k, v.size()));
        List<DocSummary> summaries = docs.entrySet().stream()
                .map(en -> en.getValue().toSummary(en.getKey())).toList();
        ManifestView manifest = manifests.findById(1)
                .map(m -> new ManifestView(m.getCorpusHash(), m.getChatModel(), m.getEmbedModel(), m.getDims(),
                        m.getChunkCount(), m.getBuiltAt()))
                .orElse(null);
        return new Overview(s.dense() ? "hybrid" : "bm25", items.size(), counts, summaries, manifest);
    }

    @PostMapping("/search")
    public SearchResult search(@RequestHeader(Access.USER_HEADER) Long userId, @Valid @RequestBody SearchRequest req) {
        String track = req.track() != null ? req.track() : QueryPlanner.routeTrack(TextNorm.fold(req.query()));
        Set<String> docTypes = req.docType() == null ? null : Set.of(req.docType());
        int k = req.k() == null ? 6 : req.k();
        Retrieval r = retriever.retrieve(List.of(req.query()), new Filter(track, docTypes, null), k);
        List<SearchHit> hits = new ArrayList<>();
        for (Hit h : r.hits()) {
            Entry e = h.entry();
            hits.add(new SearchHit(e.citationId(), e.title(), e.track(), e.docType(), e.niveau(), e.section(),
                    excerpt(e.body()), h.score(), h.denseRank(), h.bm25Rank()));
        }
        return new SearchResult(r.mode(), track, req.query(), hits);
    }

    private static final Pattern FIELD = Pattern.compile("^-\\s+\\*\\*(.+?)\\*\\*\\s*:\\s*");

    private static String excerpt(String body) {
        List<String> parts = new ArrayList<>();
        for (String line : body.split("\n")) {
            String l = line.strip();
            if (l.isEmpty() || l.startsWith("#")) {
                continue;
            }
            parts.add(FIELD.matcher(l).replaceFirst("$1 : "));
        }
        String text = String.join(" · ", parts);
        return text.length() > 420 ? text.substring(0, 420) + "…" : text;
    }

    private static final class DocAcc {
        private final String docType;
        private final String track;
        private final Set<String> items = new LinkedHashSet<>();
        private final Set<String> sections = new LinkedHashSet<>();

        DocAcc(String docType, String track) {
            this.docType = docType;
            this.track = track;
        }

        void add(Entry e) {
            items.add(e.citationId());
            if (e.section() != null && !e.section().isBlank()) {
                sections.add(e.section());
            }
        }

        DocSummary toSummary(String docId) {
            return new DocSummary(docId, docType, track, items.size(), List.copyOf(sections));
        }
    }
}
