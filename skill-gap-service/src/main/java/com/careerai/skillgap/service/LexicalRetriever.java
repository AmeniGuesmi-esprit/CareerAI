package com.careerai.skillgap.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Recherche lexicale locale (sans clé ni appel réseau) : chaque texte devient un vecteur TF-IDF,
 * puis on compare la requête aux fiches par similarité cosinus. Sert de repli quand les embeddings
 * Hugging Face ne sont pas disponibles (clé absente, crédits épuisés, panne).
 */
final class LexicalRetriever {

    private static final Set<String> STOP = Set.copyOf(List.of(
            "le", "la", "les", "un", "une", "des", "du", "de", "d", "l", "et", "ou", "en", "au", "aux", "a", "est", "sont",
            "pour", "par", "sur", "dans", "avec", "sans", "que", "qui", "vous", "nous", "votre", "vos", "notre", "ce", "cet",
            "cette", "ces", "se", "sa", "son", "ses", "ne", "pas", "plus", "il", "elle", "on", "y", "the", "of", "and", "to",
            "in", "for", "with", "on", "an", "is", "are", "or", "be", "as", "at", "by", "you", "your", "poste", "profil",
            "experience", "equipe", "bac", "ans", "an", "minimum", "souhaite", "apprecie", "requis", "maitrise",
            "connaissance", "connaissances", "notions", "bonnes", "bonne", "premiere", "plus", "recherchons", "rejoignez"));

    private LexicalRetriever() {}

    /** Score de similarité cosinus (0 à 1) entre la requête et chaque document, dans l'ordre des documents. */
    static double[] scores(List<String> docs, String query) {
        int n = docs.size();
        List<Map<String, Integer>> docTf = new ArrayList<>();
        Map<String, Integer> df = new HashMap<>();
        for (String d : docs) {
            Map<String, Integer> tf = termFreq(d);
            docTf.add(tf);
            for (String t : tf.keySet()) df.merge(t, 1, Integer::sum);
        }
        Map<String, Double> q = weights(termFreq(query), df, n);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            Map<String, Double> d = weights(docTf.get(i), df, n);
            double dot = 0;
            for (Map.Entry<String, Double> e : q.entrySet()) {
                Double w = d.get(e.getKey());
                if (w != null) dot += e.getValue() * w;
            }
            out[i] = dot;
        }
        return out;
    }

    private static Map<String, Double> weights(Map<String, Integer> tf, Map<String, Integer> df, int n) {
        Map<String, Double> w = new HashMap<>();
        double norm = 0;
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            double idf = Math.log((n + 1.0) / (df.getOrDefault(e.getKey(), 0) + 1.0)) + 1.0;
            double v = (1 + Math.log(e.getValue())) * idf;
            w.put(e.getKey(), v);
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (Map.Entry<String, Double> e : w.entrySet()) e.setValue(e.getValue() / norm);
        }
        return w;
    }

    private static Map<String, Integer> termFreq(String text) {
        Map<String, Integer> tf = new HashMap<>();
        for (String t : tokens(text)) tf.merge(t, 1, Integer::sum);
        return tf;
    }

    static List<String> tokens(String text) {
        String s = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String t : s.split("[^a-z0-9]+")) {
            if (t.length() < 2 || STOP.contains(t)) continue;
            out.add(t);
            // pluriels simples : "microservices" ~ "microservice"
            if (t.length() > 4 && t.endsWith("s") && seen.add(t)) out.add(t.substring(0, t.length() - 1));
        }
        return out;
    }
}
