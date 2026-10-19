package com.careerai.compatibility_service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.*;

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

    private final Map<String, Skill> index = new HashMap<>();

    public SkillReferential(ObjectMapper mapper) throws IOException {
        try (InputStream in = new ClassPathResource("skills-referential.json").getInputStream()) {
            List<Skill> skills = mapper.readValue(in, new TypeReference<List<Skill>>() { });
            for (Skill s : skills) {
                index.put(normalize(s.nom()), s);
                s.synonymes().forEach(syn -> index.putIfAbsent(normalize(syn), s));
            }
        }
    }

    public List<LookupResult> lookup(List<String> terms) {
        if (terms == null) return List.of();
        return terms.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .distinct()
                .limit(MAX_TERMS)
                .map(this::lookupOne)
                .toList();
    }

    private LookupResult lookupOne(String term) {
        Skill s = index.get(normalize(term));
        if (s == null) return LookupResult.notFound(term);
        List<String> equivalents = new ArrayList<>();
        equivalents.add(s.nom());
        equivalents.addAll(s.synonymes());
        return new LookupResult(term, true, s.nom(), s.famille(), equivalents, s.proches());
    }

    /** "  Spring-Boot " == "spring boot" (sans casse ni accents). */
    static String normalize(String s) {
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).replace('-', ' ').replaceAll("\\s+", " ").trim();
    }
}