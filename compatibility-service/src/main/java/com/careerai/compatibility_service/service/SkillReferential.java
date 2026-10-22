package com.careerai.compatibility_service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Référentiel local de compétences, interrogé par l'agent IA via l'outil "rechercher_competences". */
@Component
public class SkillReferential {

    public record Skill(String nom, String famille, List<String> synonymes, List<String> proches) { }

    public record LookupResult(String terme, boolean trouve, String competence, String famille,
                               List<String> equivalents, List<String> proches) {
        static LookupResult notFound(String terme) {
            return new LookupResult(terme, false, null, null, List.of(), List.of());
        }
    }

    static final int MAX_TERMS = 20;

    private record PhraseKey(Pattern pattern, Skill skill) { }

    private final Map<String, Skill> index = new HashMap<>();
    /** Clés triées de la plus longue à la plus courte : "spring boot" l'emporte sur "spring" dans une expression. */
    private final List<PhraseKey> phraseKeys = new ArrayList<>();

    public SkillReferential(ObjectMapper mapper) throws IOException {
        try (InputStream in = new ClassPathResource("skills-referential.json").getInputStream()) {
            List<Skill> skills = mapper.readValue(in, new TypeReference<List<Skill>>() { });
            for (Skill s : skills) {
                index.put(normalize(s.nom()), s);
                s.synonymes().forEach(syn -> index.putIfAbsent(normalize(syn), s));
            }
        }
        index.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Skill> e) -> e.getKey().length()).reversed())
                .forEach(e -> phraseKeys.add(new PhraseKey(Pattern.compile(
                        "(?<![\\p{L}\\p{N}])" + Pattern.quote(e.getKey()) + "(?![\\p{L}\\p{N}])"), e.getValue())));
    }

    public List<LookupResult> lookup(List<String> terms) {
        if (terms == null) return List.of();
        return terms.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .distinct()
                .limit(MAX_TERMS)
                .flatMap(term -> lookupOne(term).stream())
                .toList();
    }

    /** Un intitulé exact donne un résultat ; une expression peut en donner plusieurs ; inconnu → trouve=false. */
    private List<LookupResult> lookupOne(String term) {
        String key = normalize(term);
        Skill exact = index.get(key);
        List<Skill> skills = exact != null ? List.of(exact) : containedSkills(key);
        if (skills.isEmpty()) return List.of(LookupResult.notFound(term));
        return skills.stream().map(s -> {
            List<String> equivalents = new ArrayList<>();
            equivalents.add(s.nom());
            equivalents.addAll(s.synonymes());
            return new LookupResult(term, true, s.nom(), s.famille(), equivalents, s.proches());
        }).toList();
    }

    /**
     * Les modèles envoient souvent une expression plutôt qu'un intitulé ("conteneurisation Docker",
     * "microservices Spring Boot") : on retient chaque compétence connue qui y figure comme mot entier
     * ("JS" ne matche pas "JSON"), la plus longue d'abord, sans chevauchement ("Spring" n'est pas recompté
     * dans "Spring Boot"), dans l'ordre d'apparition.
     */
    private List<Skill> containedSkills(String phrase) {
        boolean[] taken = new boolean[phrase.length()];
        TreeMap<Integer, Skill> byPosition = new TreeMap<>();
        for (PhraseKey k : phraseKeys) {
            Matcher m = k.pattern().matcher(phrase);
            while (m.find()) {
                boolean free = true;
                for (int i = m.start(); i < m.end() && free; i++) free = !taken[i];
                if (!free) continue;
                Arrays.fill(taken, m.start(), m.end(), true);
                if (!byPosition.containsValue(k.skill())) byPosition.put(m.start(), k.skill());
            }
        }
        return new ArrayList<>(byPosition.values());
    }

    /** "  Spring-Boot " == "spring boot" (sans casse ni accents). */
    static String normalize(String s) {
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).replace('-', ' ').replaceAll("\\s+", " ").trim();
    }
}