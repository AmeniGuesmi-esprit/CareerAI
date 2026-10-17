package com.careerai.interview.rag;

import com.careerai.interview.ai.OllamaClient;
import com.careerai.interview.config.InterviewConfig.RagProperties;
import com.careerai.interview.exception.ApiException;
import com.careerai.interview.rag.RagIndex.Entry;
import com.careerai.interview.rag.RagIndex.Snapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class RagRetriever {

    public record Filter(String track, Set<String> docTypes, String level) { }

    public record Hit(Entry entry, double score, Integer denseRank, Integer bm25Rank) { }

    public record Retrieval(List<Hit> hits, String mode) { }

    private static final int CANDIDATES = 40;

    private final RagIndex index;
    private final OllamaClient ollama;
    private final RagProperties rag;

    public RagRetriever(RagIndex index, OllamaClient ollama, RagProperties rag) {
        this.index = index;
        this.ollama = ollama;
        this.rag = rag;
    }

    public Retrieval retrieve(List<String> queries, Filter filter, int k) {
        Snapshot s = index.snapshot();
        if (s.isEmpty() || queries.isEmpty()) {
            return new Retrieval(List.of(), "vide");
        }
        List<float[]> vectors = embedQueries(s, queries);
        String mode = vectors == null ? "bm25" : "hybrid";
        Map<Integer, Acc> pool = fuse(s, queries, vectors, filter, true);
        if (pool.size() < k) {
            pool = fuse(s, queries, vectors, filter, false);
        }
        List<Map.Entry<Integer, Acc>> ranked = new ArrayList<>(pool.entrySet());
        ranked.sort((a, b) -> Double.compare(b.getValue().score, a.getValue().score));
        return new Retrieval(mmr(s, ranked, k), mode);
    }

    private List<float[]> embedQueries(Snapshot s, List<String> queries) {
        if (!s.dense()) {
            return null;
        }
        try {
            return ollama.embed(queries);
        } catch (ApiException e) {
            log.warn("Recherche dense désactivée pour cette requête ({}) : repli BM25", e.getCode());
            return null;
        }
    }

    private Map<Integer, Acc> fuse(Snapshot s, List<String> queries, List<float[]> vectors, Filter filter, boolean strictLevel) {
        Map<Integer, Acc> pool = new LinkedHashMap<>();
        for (int q = 0; q < queries.size(); q++) {
            List<Integer> bm25 = s.bm25(queries.get(q), CANDIDATES);
            List<Integer> dense = vectors == null ? List.of() : s.dense(vectors.get(q), CANDIDATES);
            addRanks(s, pool, bm25, filter, strictLevel, false);
            addRanks(s, pool, dense, filter, strictLevel, true);
        }
        return pool;
    }

    private void addRanks(Snapshot s, Map<Integer, Acc> pool, List<Integer> ranking, Filter filter,
                          boolean strictLevel, boolean dense) {
        int rank = 0;
        for (int idx : ranking) {
            if (!passes(s.entries().get(idx), filter, strictLevel)) {
                continue;
            }
            rank++;
            Acc acc = pool.computeIfAbsent(idx, i -> new Acc());
            acc.score += 1.0 / (rag.rrfK() + rank);
            if (dense) {
                acc.denseRank = acc.denseRank == null ? rank : Math.min(acc.denseRank, rank);
            } else {
                acc.bm25Rank = acc.bm25Rank == null ? rank : Math.min(acc.bm25Rank, rank);
            }
        }
    }

    static boolean passes(Entry e, Filter f, boolean strictLevel) {
        if (!(e.track().equals(f.track()) || e.track().equals("generic"))) {
            return false;
        }
        if (f.docTypes() != null && !f.docTypes().isEmpty() && !f.docTypes().contains(e.docType())) {
            return false;
        }
        return !strictLevel || f.level() == null || e.niveau() == null
                || "all".equals(e.niveau()) || e.niveau().equals(f.level());
    }

    private List<Hit> mmr(Snapshot s, List<Map.Entry<Integer, Acc>> ranked, int k) {
        List<Hit> selected = new ArrayList<>();
        if (ranked.isEmpty()) {
            return selected;
        }
        double max = ranked.get(0).getValue().score;
        List<Integer> chosen = new ArrayList<>();
        List<Map.Entry<Integer, Acc>> remaining = new ArrayList<>(ranked);
        while (chosen.size() < k && !remaining.isEmpty()) {
            Map.Entry<Integer, Acc> best = null;
            double bestValue = Double.NEGATIVE_INFINITY;
            for (Map.Entry<Integer, Acc> cand : remaining) {
                double redundancy = 0;
                for (int c : chosen) {
                    redundancy = Math.max(redundancy, similarity(s.entries().get(cand.getKey()), s.entries().get(c)));
                }
                double value = rag.mmrLambda() * (cand.getValue().score / max) - (1 - rag.mmrLambda()) * redundancy;
                if (value > bestValue) {
                    bestValue = value;
                    best = cand;
                }
            }
            remaining.remove(best);
            chosen.add(best.getKey());
            Acc acc = best.getValue();
            selected.add(new Hit(s.entries().get(best.getKey()), acc.score, acc.denseRank, acc.bm25Rank));
        }
        return selected;
    }

    private static double similarity(Entry a, Entry b) {
        if (a.vector() != null && b.vector() != null) {
            return RagIndex.dot(a.vector(), b.vector());
        }
        Set<String> ta = new HashSet<>(Arrays.asList(TextNorm.fold(a.body()).split("\\W+")));
        Set<String> tb = new HashSet<>(Arrays.asList(TextNorm.fold(b.body()).split("\\W+")));
        Set<String> inter = new HashSet<>(ta);
        inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return union.isEmpty() ? 0 : (double) inter.size() / union.size();
    }

    private static final class Acc {
        double score;
        Integer denseRank;
        Integer bm25Rank;
    }
}
