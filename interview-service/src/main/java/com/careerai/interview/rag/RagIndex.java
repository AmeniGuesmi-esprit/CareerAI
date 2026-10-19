package com.careerai.interview.rag;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class RagIndex {

    public record Entry(String chunkId, String citationId, String track, String docType, String niveau,
                        String section, String title, List<String> tags, String body, float[] vector) { }

    public record Snapshot(List<Entry> entries, DirectoryReader reader, IndexSearcher searcher,
                           boolean dense, Set<String> vocabulary, FrenchAnalyzer analyzer) {

        static Snapshot empty() {
            return new Snapshot(List.of(), null, null, false, Set.of(), new FrenchAnalyzer());
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }

        public List<Integer> bm25(String text, int n) {
            if (searcher == null) {
                return List.of();
            }
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            for (String token : tokens(analyzer, text)) {
                builder.add(new TermQuery(new Term("text", token)), BooleanClause.Occur.SHOULD);
            }
            try {
                BooleanQuery query = builder.build();
                if (query.clauses().isEmpty()) {
                    return List.of();
                }
                TopDocs top = searcher.search(query, n);
                List<Integer> out = new ArrayList<>();
                for (ScoreDoc sd : top.scoreDocs) {
                    out.add(Integer.parseInt(searcher.doc(sd.doc).get("i")));
                }
                return out;
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Recherche BM25 impossible", e);
            }
        }

        public List<Integer> dense(float[] query, int n) {
            if (!dense || query == null) {
                return List.of();
            }
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).vector() != null) {
                    idx.add(i);
                }
            }
            idx.sort(Comparator.comparingDouble((Integer i) -> -dot(query, entries.get(i).vector())));
            return idx.subList(0, Math.min(n, idx.size()));
        }
    }

    private final RagChunkRepository chunks;
    private final FrenchAnalyzer analyzer = new FrenchAnalyzer();
    private volatile Snapshot snapshot = Snapshot.empty();

    public RagIndex(RagChunkRepository chunks) {
        this.chunks = chunks;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        reload();
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public synchronized void reload() {
        List<RagChunk> rows = new ArrayList<>(chunks.findAll());
        rows.sort(Comparator.comparing(RagChunk::getCitationId).thenComparing(RagChunk::getChunkId));
        List<Entry> entries = new ArrayList<>(rows.size());
        Set<String> vocabulary = new LinkedHashSet<>();
        boolean dense = false;
        try {
            Directory directory = new ByteBuffersDirectory();
            try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
                for (int i = 0; i < rows.size(); i++) {
                    RagChunk r = rows.get(i);
                    float[] vector = r.getEmbedding() == null ? null : decode(r.getEmbedding());
                    dense |= vector != null;
                    List<String> tags = r.getTags() == null || r.getTags().isBlank()
                            ? List.of() : Arrays.stream(r.getTags().split(",")).map(String::strip).toList();
                    vocabulary.addAll(tags);
                    entries.add(new Entry(r.getChunkId(), r.getCitationId(), r.getTrack(), r.getDocType(),
                            r.getNiveau(), r.getSection(), r.getTitle(), tags, r.getBody(), vector));
                    Document doc = new Document();
                    doc.add(new StringField("i", String.valueOf(i), Field.Store.YES));
                    doc.add(new TextField("text", r.getIndexText(), Field.Store.NO));
                    writer.addDocument(doc);
                }
            }
            DirectoryReader reader = DirectoryReader.open(directory);
            Snapshot next = new Snapshot(List.copyOf(entries), reader, new IndexSearcher(reader), dense,
                    Set.copyOf(vocabulary), analyzer);
            snapshot = next;
            log.info("Index RAG chargé : {} chunks (dense={})", entries.size(), dense);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Construction de l'index BM25 impossible", e);
        }
    }

    public static List<String> tokens(FrenchAnalyzer analyzer, String text) {
        Set<String> out = new HashSet<>();
        try (TokenStream ts = analyzer.tokenStream("text", text)) {
            CharTermAttribute att = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken() && out.size() < 96) {
                out.add(att.toString());
            }
            ts.end();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Analyse du texte impossible", e);
        }
        return new ArrayList<>(out);
    }

    public static byte[] encode(float[] v) {
        ByteBuffer buf = ByteBuffer.allocate(v.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : v) {
            buf.putFloat(f);
        }
        return buf.array();
    }

    public static float[] decode(byte[] bytes) {
        float[] v = new float[bytes.length / Float.BYTES];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(v);
        return v;
    }

    static double dot(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length && i < b.length; i++) {
            s += a[i] * b[i];
        }
        return s;
    }
}
