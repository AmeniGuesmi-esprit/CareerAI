package com.careerai.compatibility_service.service;

import com.careerai.compatibility_service.dto.AnalysisDtos.AiMetrics;
import com.careerai.compatibility_service.dto.AnalysisDtos.AiResult;
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
import java.util.Map;
import java.util.Optional;

/**
 * Cache mémoire des analyses validées. Une analyse locale prend plusieurs minutes sur CPU : une demande identique
 * (même modèle, même prompt, même CV et même offre APRÈS anonymisation) est resservie sans rappeler Ollama.
 * La clé est un SHA-256 : aucun texte de CV n'est conservé en clair dans les clés. Seules les réponses valides
 * sont mises en cache (le résultat est un record immuable). Éviction LRU et durée de vie configurables.
 */
@Component
public class AnalysisCache {

    public record Entry(AiResult result, AiMetrics metrics) { }

    private record Cached(Entry entry, long expiresAt) { }

    private final long ttlMillis;
    private final int maxEntries;
    private final Clock clock;
    private final Map<String, Cached> entries;
    private long hits;
    private long misses;

    @Autowired
    public AnalysisCache(@Value("${app.ai.cache.ttl-minutes:60}") long ttlMinutes,
                         @Value("${app.ai.cache.max-entries:200}") int maxEntries) {
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

    /** SHA-256 du modèle, des consignes et des textes anonymisés (séparateur \0 : pas de collision par concaténation). */
    public static String key(String model, String prompt, String cvText, String jobText) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            String material = String.join("\u0000", model, prompt, cvText, jobText);
            return HexFormat.of().formatHex(sha.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    public synchronized Optional<Entry> get(String key) {
        if (!enabled()) return Optional.empty();
        Cached c = entries.get(key);
        if (c != null && clock.millis() >= c.expiresAt()) {
            entries.remove(key);
            c = null;
        }
        if (c == null) {
            misses++;
            return Optional.empty();
        }
        hits++;
        return Optional.of(c.entry());
    }

    public synchronized void put(String key, AiResult result, AiMetrics metrics) {
        if (!enabled()) return;
        entries.put(key, new Cached(new Entry(result, metrics), clock.millis() + ttlMillis));
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized long hits() {
        return hits;
    }

    public synchronized long misses() {
        return misses;
    }
}
