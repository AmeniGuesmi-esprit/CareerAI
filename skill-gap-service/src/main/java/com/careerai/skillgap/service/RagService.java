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
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RAG (Retrieval-Augmented Generation) :
 * 1. base de connaissances : fiches de compétences (knowledge/*.json, un fichier par domaine) stockées en MySQL ;
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

    /** Appels d'embeddings : 16 blocs de 400 caractères couvrent une offre de 6000 caractères en un seul lot HF. */
    private static final int MAX_CHUNKS = 16;
    private static final long RETRY_DELAY_MS = 10 * 60 * 1000L;
    static final String KNOWLEDGE_FILES = "classpath*:knowledge/*.json";

    /**
     * Rubriques d'une offre qui décrivent l'entreprise ou les conditions, pas les compétences demandées.
     * Sans ce filtre, « Processus de recrutement : entretien... » ou « Nous offrons : formation continue »
     * feraient remonter les fiches RH ou formation pour n'importe quelle offre.
     */
    private static final List<String> BOILERPLATE_HEADERS = List.of(
            "avantages", "nous offrons", "nous vous offrons", "ce que nous offrons", "nous proposons",
            "ce que nous proposons", "pourquoi nous rejoindre", "qui sommes", "a propos", "l entreprise",
            "notre entreprise", "notre societe", "presentation de l entreprise", "processus de recrutement",
            "deroulement du recrutement", "etapes du recrutement", "remuneration", "salaire", "localisation",
            "lieu de travail", "type de contrat", "contrat", "date de debut", "comment postuler", "pour postuler",
            "candidature", "what we offer", "benefits", "perks", "about us", "location", "salary", "how to apply");
    /** « Libellé : suite » en début de ligne (puce éventuelle), libellé court. */
    private static final Pattern HEADER = Pattern.compile("^[-•*\\s]*([^:]{2,40}?)\\s*:(.*)$");

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
    @Value("${rag.lexical-chunk-chars:250}")
    private int lexicalChunkChars;
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
            String query = requirementsText(jobText);
            List<Scored> scored = null;
            String mode = "lexical";
            if (embeddings.available() && System.currentTimeMillis() >= semanticRetryAt) {
                try {
                    scored = semantic(query);
                    mode = "sémantique";
                } catch (Exception e) {
                    semanticRetryAt = System.currentTimeMillis() + RETRY_DELAY_MS;
                    log.warn("RAG : embeddings indisponibles ({}). Repli sur la recherche lexicale locale.", e.getMessage());
                }
            }
            if (scored == null) scored = lexical(query);
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
        List<float[]> queries = embeddings.embed(chunk(jobText, chunkChars, MAX_CHUNKS));
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
        List<String> texts = docs.stream().map(d -> lexicalText(d.getSkill(), d.getContent())).toList();
        double[] scores = lexicalScores(texts, jobText, lexicalChunkChars);
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            if (scores[i] >= lexicalMinScore) scored.add(new Scored(docs.get(i), scores[i]));
        }
        return scored;
    }

    // ── Indexation ───────────────────────────────────────────────────────────

    /**
     * Toutes les fiches des fichiers knowledge/*.json (un fichier par domaine : informatique, finance, santé...).
     * Pour ajouter un domaine, il suffit d'ajouter un fichier : il est chargé au prochain démarrage.
     */
    static List<JsonNode> readKnowledgeBase(ObjectMapper mapper) throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources(KNOWLEDGE_FILES);
        Arrays.sort(files, Comparator.comparing(Resource::getFilename));
        List<JsonNode> docs = new ArrayList<>();
        for (Resource file : files) {
            try (InputStream in = file.getInputStream()) {
                for (JsonNode n : mapper.readTree(in)) docs.add(n);
            }
        }
        // garde-fou : une base vide supprimerait toutes les fiches de MySQL (voir loadKnowledgeBase)
        if (docs.isEmpty()) throw new IllegalStateException("aucune fiche trouvée dans " + KNOWLEDGE_FILES);
        return docs;
    }

    private void loadKnowledgeBase() throws Exception {
        Set<String> slugs = new HashSet<>();
        for (JsonNode n : readKnowledgeBase(mapper)) {
            String slug = n.path("slug").asText();
            String content = n.path("content").asText();
            if (slug.isBlank() || content.isBlank()) {
                log.warn("RAG : fiche ignorée (slug ou contenu manquant) : {}", n.path("skill").asText());
                continue;
            }
            if (!slugs.add(slug)) {
                log.warn("RAG : slug en double ignoré : {}", slug);
                continue;
            }
            Optional<SkillDoc> existing = repository.findBySlug(slug);
            SkillDoc doc = existing.orElseGet(SkillDoc::new);
            String skill = n.path("skill").asText();
            String category = n.path("category").asText(null);
            boolean changed = existing.isEmpty() || !content.equals(doc.getContent())
                    || !skill.equals(doc.getSkill()) || !Objects.equals(category, doc.getCategory());
            // vecteur produit par un autre modèle (HF_EMBEDDING_MODEL modifié) : non comparable
            boolean staleVector = doc.getEmbedding() != null && embeddings.available()
                    && !embeddings.model().equals(doc.getEmbeddingModel());
            if (!changed && !staleVector) continue;
            doc.setSlug(slug);
            doc.setSkill(skill);
            doc.setCategory(category);
            doc.setContent(content);
            doc.setEmbedding(null);   // fiche nouvelle ou modifiée : à re-vectoriser
            doc.setEmbeddingModel(null);
            repository.save(doc);
        }
        log.info("RAG : base de connaissances chargée ({} fiches)", slugs.size());
        // fiches retirées du JSON : elles ne doivent plus être proposées au LLM
        List<SkillDoc> removed = repository.findAll().stream().filter(d -> !slugs.contains(d.getSlug())).toList();
        if (!removed.isEmpty()) {
            repository.deleteAll(removed);
            log.info("RAG : {} fiche(s) retirée(s) de la base de connaissances", removed.size());
        }
    }

    private synchronized void indexMissing() throws Exception {
        List<SkillDoc> missing = repository.findByEmbeddingIsNull();
        if (missing.isEmpty() || !embeddings.available()) return;
        List<String> texts = missing.stream().map(d -> d.getSkill() + ". " + d.getContent()).toList();
        List<float[]> vectors = embeddings.embed(texts);
        for (int i = 0; i < missing.size(); i++) {
            missing.get(i).setEmbedding(mapper.writeValueAsString(vectors.get(i)));
            missing.get(i).setEmbeddingModel(embeddings.model());
        }
        repository.saveAll(missing);
        log.info("RAG : {} fiche(s) vectorisée(s)", missing.size());
    }

    // ── Outils ───────────────────────────────────────────────────────────────

    /**
     * Partie de l'offre utile à la recherche : retire les rubriques de présentation (avantages, processus de
     * recrutement, rémunération...). Une rubrique écartée court jusqu'au prochain libellé ou à la prochaine ligne vide.
     * Le LLM, lui, reçoit toujours l'offre complète.
     */
    static String requirementsText(String offer) {
        StringBuilder out = new StringBuilder();
        boolean skipping = false;
        for (String line : offer.split("\\R")) {
            if (line.isBlank()) {
                skipping = false;
                out.append('\n');
                continue;
            }
            Matcher h = HEADER.matcher(line);
            boolean header = h.matches();
            // « Avantages : ... » : le libellé commence par une rubrique ; ligne seule « Avantages » : égalité stricte
            boolean boilerplate = header ? isBoilerplateHeader(h.group(1), false) : isBoilerplateHeader(line, true);
            if (header || boilerplate) skipping = boilerplate;
            if (!skipping) out.append(line).append('\n');
        }
        String kept = out.toString().strip();
        return kept.isEmpty() ? offer : kept;
    }

    private static boolean isBoilerplateHeader(String label, boolean exact) {
        String l = Normalizer.normalize(label.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").strip();
        return l.length() <= 40 && BOILERPLATE_HEADERS.stream().anyMatch(b -> exact ? l.equals(b) : l.startsWith(b));
    }

    static String lexicalText(String skill, String content) {
        return (skill + " ").repeat(3) + content;
    }

    /**
     * Score lexical de chaque fiche = meilleur score sur les blocs de l'offre (comme la recherche sémantique).
     * Comparer l'offre entière diluerait le score : la présentation de l'entreprise, les avantages, etc.
     * pèsent dans la norme TF-IDF, et sur une offre réaliste plus aucune fiche n'atteint le seuil.
     * Pas de plafond de blocs ici (calcul local) : les compétences sont souvent en fin d'offre.
     */
    static double[] lexicalScores(List<String> docTexts, String jobText, int chunkChars) {
        double[] best = new double[docTexts.size()];
        for (String c : chunk(jobText, chunkChars, Integer.MAX_VALUE)) {
            double[] s = LexicalRetriever.scores(docTexts, c);
            for (int i = 0; i < best.length; i++) best[i] = Math.max(best[i], s[i]);
        }
        return best;
    }

    /** Découpe le texte en blocs d'environ chunkChars caractères, en coupant sur les fins de ligne ou de phrase. */
    static List<String> chunk(String text, int chunkChars, int maxChunks) {
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
        return chunks.size() > maxChunks ? chunks.subList(0, maxChunks) : chunks;
    }

    /** Vecteurs normalisés : produit scalaire = similarité cosinus. */
    static double dot(float[] a, float[] b) {
        // dimensions différentes = modèles différents : on échoue (repli lexical) plutôt que de renvoyer un score faux
        if (a.length != b.length) throw new IllegalStateException("dimensions d'embedding incompatibles");
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }
}