package com.careerai.skillgap.service;

import com.careerai.skillgap.model.SkillDoc;
import com.careerai.skillgap.repository.SkillDocRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * RAG (Retrieval-Augmented Generation) :
 * 1. base de connaissances : fiches de compétences (skills-knowledge.json) stockées en MySQL ;
 * 2. vectorisation : embeddings Hugging Face, calculés une seule fois par fiche ;
 * 3. recherche : l'offre est découpée en blocs (chunking), chaque bloc est comparé aux fiches
 *    par similarité cosinus, on garde les top-k fiches ;
 * 4. génération : les fiches retenues sont insérées dans le prompt (balise &lt;contexte&gt;).
 * Deux modes de recherche, choisis automatiquement :
 *  - sémantique : embeddings Hugging Face (si HF_API_KEY est définie et fonctionne) ;
 *  - lexical : vecteurs TF-IDF calculés localement (repli sans clé, sans réseau, sans crédits).
 * En cas d'échec total, le RAG est ignoré et l'analyse continue sans contexte.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private static final int MAX_CHUNKS = 12;
    private static final long RETRY_DELAY_MS = 10 * 60 * 1000L;

    private record Scored(SkillDoc doc, double score) {}

    /** Après un échec de l'API d'embeddings, on évite de la rappeler pendant 10 minutes. */
    private volatile long semanticRetryAt = 0;

    private final SkillDocRepository repository;
    private final EmbeddingClient embeddings;
    private final ObjectMapper mapper;

    @Value("${rag.top-k:5}")
    private int topK;
    @Value("${rag.min-score:0.30}")
    private double minScore;
    @Value("${rag.lexical-min-score:0.10}")
    private double lexicalMinScore;
    @Value("${rag.chunk-chars:400}")
    private int chunkChars;
    @Value("${rag.context-max-chars:1800}")
    private int contextMaxChars;

    /** Au démarrage : charge les fiches du JSON puis, si possible, calcule les embeddings manquants. */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            loadKnowledgeBase();
        } catch (Exception e) {
            log.warn("RAG : chargement de la base de connaissances impossible ({})", e.getMessage());
            return;
        }
        if (!embeddings.available()) {
            log.info("RAG : HF_API_KEY absente, recherche lexicale locale (TF-IDF) utilisée");
            return;
        }
        try {
            indexMissing();
        } catch (Exception e) {
            semanticRetryAt = System.currentTimeMillis() + RETRY_DELAY_MS;
            log.warn("RAG : embeddings indisponibles ({}). Repli sur la recherche lexicale locale.", e.getMessage());
        }
    }

    /** Retourne le contexte à insérer dans le prompt ("" si aucune fiche pertinente). */
    public String retrieve(String jobText) {
        return retrieve(jobText, null);
    }

    /**
     * Idem, mais écarte les fiches des compétences que le CV démontre déjà : le contexte sert à
     * conseiller des ressources pour les compétences MANQUANTES, inutile d'y gaspiller la place.
     */
    public String retrieve(String jobText, String cvText) {
        if (jobText == null || jobText.isBlank()) return "";
        try {
            List<Scored> scored = null;
            String mode = "lexical";
            if (embeddings.available() && System.currentTimeMillis() >= semanticRetryAt) {
                try {
                    scored = semantic(jobText);
                    mode = "sémantique";
                } catch (Exception e) {
                    semanticRetryAt = System.currentTimeMillis() + RETRY_DELAY_MS;
                    log.warn("RAG : embeddings indisponibles ({}). Repli sur la recherche lexicale locale.", e.getMessage());
                }
            }
            if (scored == null) scored = lexical(jobText);
            scored.sort(Comparator.comparingDouble(Scored::score).reversed());

            CvCoverage coverage = (cvText == null || cvText.isBlank()) ? null : new CvCoverage(cvText);
            StringBuilder ctx = new StringBuilder();
            int kept = 0;
            for (Scored s : scored) {
                if (kept >= topK) break;
                if (coverage != null && coverage.covers(s.doc().getSkill())) {
                    log.debug("RAG ({}) : fiche ignorée {} (déjà démontrée dans le CV)", mode, s.doc().getSkill());
                    continue;
                }
                String line = "- " + s.doc().getSkill() + " : " + s.doc().getContent() + "\n";
                if (ctx.length() + line.length() > contextMaxChars) break;
                ctx.append(line);
                kept++;
                log.debug("RAG ({}) : fiche retenue {} (similarité {})", mode, s.doc().getSkill(),
                        String.format("%.2f", s.score()));
            }
            return ctx.toString().strip();
        } catch (Exception e) {
            log.warn("RAG indisponible, analyse sans contexte : {}", e.getMessage());
            return "";
        }
    }

    /** Recherche sémantique : embeddings de l'offre (par blocs) comparés aux embeddings des fiches. */
    private List<Scored> semantic(String jobText) throws Exception {
        indexMissing();
        List<SkillDoc> docs = repository.findByEmbeddingIsNotNull();
        if (docs.isEmpty()) throw new IllegalStateException("aucune fiche vectorisée");
        List<float[]> queries = embeddings.embed(chunk(jobText));
        List<Scored> scored = new ArrayList<>();
        for (SkillDoc d : docs) {
            float[] v = mapper.readValue(d.getEmbedding(), float[].class);
            double best = -1;
            for (float[] q : queries) best = Math.max(best, dot(q, v));
            if (best >= minScore) scored.add(new Scored(d, best));
        }
        return scored;
    }

    /** Recherche lexicale locale : TF-IDF + similarité cosinus (le nom de la compétence compte triple). */
    private List<Scored> lexical(String jobText) {
        List<SkillDoc> docs = repository.findAll();
        List<String> texts = docs.stream()
                .map(d -> (d.getSkill() + " ").repeat(3) + d.getContent())
                .toList();
        double[] scores = LexicalRetriever.scores(texts, jobText);
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            if (scores[i] >= lexicalMinScore) scored.add(new Scored(docs.get(i), scores[i]));
        }
        return scored;
    }

    // ── Indexation ───────────────────────────────────────────────────────────

    private void loadKnowledgeBase() throws Exception {
        try (InputStream in = new ClassPathResource("skills-knowledge.json").getInputStream()) {
            JsonNode root = mapper.readTree(in);
            for (JsonNode n : root) {
                String slug = n.path("slug").asText();
                String content = n.path("content").asText();
                Optional<SkillDoc> existing = repository.findBySlug(slug);
                SkillDoc doc = existing.orElseGet(SkillDoc::new);
                boolean changed = existing.isEmpty() || !content.equals(doc.getContent())
                        || !n.path("skill").asText().equals(doc.getSkill());
                if (!changed) continue;
                doc.setSlug(slug);
                doc.setSkill(n.path("skill").asText());
                doc.setCategory(n.path("category").asText(null));
                doc.setContent(content);
                doc.setEmbedding(null);   // fiche nouvelle ou modifiée : à re-vectoriser
                repository.save(doc);
            }
        }
    }

    private synchronized void indexMissing() throws Exception {
        List<SkillDoc> missing = repository.findByEmbeddingIsNull();
        if (missing.isEmpty() || !embeddings.available()) return;
        List<String> texts = missing.stream().map(d -> d.getSkill() + ". " + d.getContent()).toList();
        List<float[]> vectors = embeddings.embed(texts);
        for (int i = 0; i < missing.size(); i++) {
            missing.get(i).setEmbedding(mapper.writeValueAsString(vectors.get(i)));
        }
        repository.saveAll(missing);
        log.info("RAG : {} fiche(s) vectorisée(s)", missing.size());
    }

    // ── Outils ───────────────────────────────────────────────────────────────

    /** Découpe le texte en blocs d'environ chunkChars caractères, en coupant sur les fins de ligne ou de phrase. */
    List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String part : text.split("(?<=[.!?:;])\\s+|\\R+")) {
            String p = part.strip();
            if (p.isEmpty()) continue;
            if (cur.length() > 0 && cur.length() + p.length() > chunkChars) {
                chunks.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(p);
        }
        if (cur.length() > 0) chunks.add(cur.toString());
        return chunks.size() > MAX_CHUNKS ? chunks.subList(0, MAX_CHUNKS) : chunks;
    }

    /** Vecteurs normalisés : produit scalaire = similarité cosinus. */
    static double dot(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double s = 0;
        for (int i = 0; i < n; i++) s += a[i] * b[i];
        return s;
    }
}