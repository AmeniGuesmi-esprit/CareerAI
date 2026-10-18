package com.careerai.interview.rag;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KnowledgeParser {

    public static final Set<String> TRACKS = Set.of("backend-java-spring", "frontend-angular", "generic");
    public static final Set<String> DOC_TYPES = Set.of("question_bank", "guide", "rubric");
    public static final Set<String> LEVELS = Set.of("junior", "confirme", "senior");
    public static final Set<String> CATEGORIES = Set.of("technique", "comportementale");

    static final int MAX_CHUNK_CHARS = 1_800;
    private static final Pattern FIELD = Pattern.compile("^-\\s+\\*\\*(.+?)\\*\\*\\s*:\\s*(.+)$");
    private static final List<String> REQUIRED_QB_FIELDS = List.of(
            "categorie", "niveau", "tags", "intention", "points attendus", "reponse modele (plan)", "pieges");

    public record KnowledgeChunk(String chunkId, String docId, String citationId, String track, String docType,
                                 String niveau, String section, String title, List<String> tags,
                                 String body, String indexText, String contentHash) { }

    public record KnowledgeDocument(String docId, String fileName, String track, String docType, String version,
                                    String contentHash, List<KnowledgeChunk> chunks) { }

    public KnowledgeDocument parse(Path file) {
        String raw;
        try {
            raw = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException("Lecture impossible : " + file, e);
        }
        String fileName = file.getFileName().toString();
        Map<String, String> front = frontMatter(raw, fileName);
        String docId = required(front, "id", fileName);
        String track = required(front, "track", fileName);
        String docType = required(front, "doc_type", fileName);
        String version = required(front, "version", fileName);
        if (!TRACKS.contains(track)) {
            throw new IllegalStateException(fileName + " : track invalide '" + track + "'");
        }
        if (!DOC_TYPES.contains(docType)) {
            throw new IllegalStateException(fileName + " : doc_type invalide '" + docType + "'");
        }
        if (!"fr".equals(front.get("lang"))) {
            throw new IllegalStateException(fileName + " : lang doit être 'fr'");
        }
        String body = raw.substring(raw.indexOf("\n---", 3) + 4);
        List<KnowledgeChunk> chunks = chunks(body, fileName, docId, track, docType);
        return new KnowledgeDocument(docId, fileName, track, docType, version, sha256(raw.getBytes(StandardCharsets.UTF_8)), chunks);
    }

    private List<KnowledgeChunk> chunks(String body, String fileName, String docId, String track, String docType) {
        List<KnowledgeChunk> out = new ArrayList<>();
        Set<String> seenCitations = new java.util.HashSet<>();
        String section = "";
        String[] lines = body.split("\n");
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            if (line.startsWith("## ")) {
                section = line.substring(3).trim();
                i++;
                continue;
            }
            if (!line.startsWith("### ")) {
                i++;
                continue;
            }
            String title = line.substring(4).trim();
            List<String> itemLines = new ArrayList<>();
            i++;
            while (i < lines.length && !lines[i].startsWith("### ") && !lines[i].startsWith("## ")) {
                itemLines.add(lines[i]);
                i++;
            }
            String slug = TextNorm.slug(title);
            String citation = docId + "/" + slug;
            if (slug.isBlank() || !seenCitations.add(citation)) {
                throw new IllegalStateException(fileName + " : titre d'item en double ou vide '" + title + "'");
            }
            Map<String, String> fields = fields(itemLines);
            String niveau = "all";
            List<String> tags = splitList(fields.getOrDefault("tags", ""));
            if ("question_bank".equals(docType)) {
                for (String f : REQUIRED_QB_FIELDS) {
                    if (!fields.containsKey(f) || fields.get(f).isBlank()) {
                        throw new IllegalStateException(fileName + " : champ '" + f + "' manquant dans '" + title + "'");
                    }
                }
                niveau = fields.get("niveau").trim();
                if (!LEVELS.contains(niveau)) {
                    throw new IllegalStateException(fileName + " : niveau invalide '" + niveau + "' dans '" + title + "'");
                }
                String cat = fields.get("categorie").trim();
                if (!CATEGORIES.contains(cat)) {
                    throw new IllegalStateException(fileName + " : catégorie invalide '" + cat + "' dans '" + title + "'");
                }
                if (tags.isEmpty()) {
                    throw new IllegalStateException(fileName + " : tags vides dans '" + title + "'");
                }
            }
            List<String> parts = split(itemLines);
            for (int p = 0; p < parts.size(); p++) {
                String partBody = parts.get(p);
                String fullBody = "### " + title + "\n" + partBody.strip();
                String indexText = "[" + track + " | " + niveau + " | " + section + "] " + title + "\n" + partBody.strip();
                String chunkId = sha256((citation + "#" + p).getBytes(StandardCharsets.UTF_8));
                out.add(new KnowledgeChunk(chunkId, docId, citation, track, docType, niveau, section, title, tags,
                        fullBody, indexText, sha256(indexText.getBytes(StandardCharsets.UTF_8))));
            }
        }
        if (out.isEmpty()) {
            throw new IllegalStateException(fileName + " : aucun item '###' trouvé");
        }
        return out;
    }

    private static List<String> split(List<String> itemLines) {
        String text = String.join("\n", itemLines).strip();
        if (text.length() <= MAX_CHUNK_CHARS) {
            return List.of(text);
        }
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String previousLine = null;
        for (String line : text.split("\n")) {
            if (current.length() + line.length() > MAX_CHUNK_CHARS && current.length() > 0) {
                result.add(current.toString().strip());
                current = new StringBuilder();
                if (previousLine != null) {
                    current.append(previousLine).append('\n');
                }
            }
            current.append(line).append('\n');
            previousLine = line;
        }
        if (current.length() > 0) {
            result.add(current.toString().strip());
        }
        return result;
    }

    private static Map<String, String> fields(List<String> itemLines) {
        Map<String, String> fields = new HashMap<>();
        for (String line : itemLines) {
            Matcher m = FIELD.matcher(line.strip());
            if (m.matches()) {
                fields.put(TextNorm.fold(m.group(1)).strip(), m.group(2).strip());
            }
        }
        return fields;
    }

    private static Map<String, String> frontMatter(String raw, String fileName) {
        if (!raw.startsWith("---\n") || raw.indexOf("\n---", 3) < 0) {
            throw new IllegalStateException(fileName + " : front matter YAML absent");
        }
        String block = raw.substring(4, raw.indexOf("\n---", 3));
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : block.split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                map.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
            }
        }
        return map;
    }

    private static String required(Map<String, String> front, String key, String fileName) {
        String v = front.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(fileName + " : champ front matter '" + key + "' manquant");
        }
        return v;
    }

    private static List<String> splitList(String value) {
        List<String> out = new ArrayList<>();
        for (String part : value.split("[,;]")) {
            String t = TextNorm.fold(part).strip();
            if (!t.isBlank() && !out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
