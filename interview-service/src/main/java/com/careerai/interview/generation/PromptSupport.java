package com.careerai.interview.generation;

import com.careerai.interview.rag.TextNorm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class PromptSupport {

    private static final Pattern FORGED_TAG = Pattern.compile(
            "(?i)</?(cv|offre|ressources|question|intention|points_attendus|reponse_candidat|"
                    + "competences_correspondantes|lacunes|analyse_compatibilite)>");

    private PromptSupport() { }

    static String tag(String name, String content) {
        String safe = FORGED_TAG.matcher(content == null ? "" : content).replaceAll("[balise]");
        return "<" + name + ">\n" + safe + "\n</" + name + ">\n\n";
    }

    static String truncate(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) : s);
    }

    static JsonNode parseJson(ObjectMapper mapper, String raw) throws JsonProcessingException {
        String s = raw == null ? "" : raw.strip().replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        int obj = s.indexOf('{');
        int arr = s.indexOf('[');
        int start = obj < 0 ? arr : (arr < 0 ? obj : Math.min(obj, arr));
        int end = Math.max(s.lastIndexOf('}'), s.lastIndexOf(']'));
        if (start < 0 || end <= start) {
            return mapper.getNodeFactory().missingNode();
        }
        return mapper.readTree(s.substring(start, end + 1));
    }

    static List<String> strings(JsonNode node, int max, int maxLen) {
        List<String> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode n : node) {
            String s = n.asText("").strip();
            if (!s.isBlank()) {
                out.add(truncate(s, maxLen));
            }
            if (out.size() >= max) {
                break;
            }
        }
        return out;
    }

    static boolean similar(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return false;
        }
        Set<String> inter = new HashSet<>(ta);
        inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return (double) inter.size() / union.size() > 0.6;
    }

    private static Set<String> tokens(String s) {
        Set<String> out = new HashSet<>();
        Arrays.stream(TextNorm.fold(s).split("[^a-z0-9]+")).filter(t -> t.length() > 2).forEach(out::add);
        return out;
    }
}
