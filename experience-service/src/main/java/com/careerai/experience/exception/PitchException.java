package com.careerai.experience.exception;

import java.util.List;

/**
 * Echec de generation du pitch (sortie invalide ou pitch rejete par le juge apres retry).
 */
public class PitchException extends RuntimeException {

    private final List<String> problemes;

    public PitchException(String message) {
        super(message);
        this.problemes = List.of();
    }

    public PitchException(String message, Throwable cause) {
        super(message, cause);
        this.problemes = List.of();
    }

    public PitchException(String message, List<String> problemes) {
        super(message);
        this.problemes = problemes == null ? List.of() : List.copyOf(problemes);
    }

    public List<String> getProblemes() {
        return problemes;
    }
}
