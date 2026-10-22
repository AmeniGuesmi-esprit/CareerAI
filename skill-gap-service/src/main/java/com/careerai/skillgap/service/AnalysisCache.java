package com.careerai.skillgap.service;

import com.careerai.skillgap.dto.AnalysisResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cache mémoire des analyses validées : une même demande (modèle + prompt système + offre et CV anonymisés)
 * est resservie sans rappeler le LLM, ce qui économise le quota Groq (8 000 jetons/min) et la latence.
 * La clé est un hash SHA-256 : aucun texte de CV n'est conservé en clair dans les clés.
 * Seules les réponses valides sont mises en cache.
 */
@Component
public class AnalysisCache {

    private final long ttlMillis;
    private final int maxEntries;
    private final Clock clock;
    private final Map<String, Cached> entries;

    private record Cached(AnalysisResult result, long expiresAt) {}

    @Autowired
    public AnalysisCache(@Value("${llm.cache.ttl-minutes:60}") long ttlMinutes,
                         @Value("${llm.cache.max-entries:200}") int maxEntries) {
        this(Duration.ofMinutes(ttlMinutes), maxEntries, Clock.systemUTC());
    }

    AnalysisCache(Duration ttl, int maxEntries, Clock clock) {
        this.ttlMillis = ttl.toMillis();
        this.maxEntries = maxEntries;
        this.clock = clock;
        // Ordre d'accès : l'entrée la moins récemment utilisée est évincée en premier
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
                return size() > AnalysisCache.this.maxEntries;
            }
        };
    }

    public boolean enabled() {
        return ttlMillis > 0 && maxEntries > 0;
    }

    /** Clé de cache : SHA-256 du modèle, du prompt système et du prompt utilisateur (textes déjà anonymisés). */
    public static String key(String model, String systemPrompt, String userPrompt) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            String material = model + "\u0000" + systemPrompt + "\u0000" + userPrompt;
            return HexFormat.of().formatHex(sha.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    public synchronized Optional<AnalysisResult> get(String key) {
        if (!enabled()) return Optional.empty();
        Cached e = entries.get(key);
        if (e == null) return Optional.empty();
        if (clock.millis() >= e.expiresAt()) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(e.result());
    }

    public synchronized void put(String key, AnalysisResult result) {
        if (!enabled()) return;
        AnalysisResult copy = new AnalysisResult(result.matchScore(), result.summary(),
                List.copyOf(result.strengths()), List.copyOf(result.missingSkills()), List.copyOf(result.cvTips()));
        entries.put(key, new Cached(copy, clock.millis() + ttlMillis));
    }

    public synchronized int size() {
        return entries.size();
    }
}
