package com.careerai.experience.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Collecte, par thread, les statistiques des appels LLM (latence, jetons, tentatives HTTP).
 * Inactif par defaut : tant que start() n'a pas ete appele, record() ne fait rien.
 * Utilise par le banc d'evaluation (PitchEvaluationTest) pour mesurer le cout et la latence d'un pitch.
 */
public final class LlmCallStats {

    /** Un appel a generateContent (retries HTTP inclus). */
    public record Call(double temperature, long latencyMs, int promptTokens, int outputTokens,
                       int thoughtsTokens, int totalTokens, int httpAttempts) {
    }

    private static final ThreadLocal<List<Call>> CURRENT = new ThreadLocal<>();

    private LlmCallStats() {
    }

    /** Demarre une collecte pour le thread courant. */
    public static void start() {
        CURRENT.set(new ArrayList<>());
    }

    /** Termine la collecte et retourne les appels enregistres depuis start(). */
    public static List<Call> stop() {
        List<Call> calls = CURRENT.get();
        CURRENT.remove();
        return calls == null ? List.of() : calls;
    }

    static void record(Call call) {
        List<Call> calls = CURRENT.get();
        if (calls != null) {
            calls.add(call);
        }
    }
}