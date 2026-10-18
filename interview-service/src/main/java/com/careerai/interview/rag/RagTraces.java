package com.careerai.interview.rag;

import java.util.List;

public final class RagTraces {

    private RagTraces() { }

    public record Hit(String citationId, String title, String docType, String niveau, String section, String track,
                      double score, Integer denseRank, Integer bm25Rank) { }

    public record Source(String citationId, String title, String docType, String niveau, String section) { }

    public record Trace(String track, String level, List<String> matchedSkills, List<String> gapSkills,
                        List<String> queries, String mode, List<Hit> hits, String promptVersion, String model,
                        Integer seed, String manifestHash) { }

    public static Hit hit(RagRetriever.Hit h) {
        RagIndex.Entry e = h.entry();
        return new Hit(e.citationId(), e.title(), e.docType(), e.niveau(), e.section(), e.track(), h.score(),
                h.denseRank(), h.bm25Rank());
    }

    public static Source source(RagIndex.Entry e) {
        return new Source(e.citationId(), e.title(), e.docType(), e.niveau(), e.section());
    }
}
