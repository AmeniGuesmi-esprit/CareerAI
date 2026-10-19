package com.careerai.experience.exception;

/** Erreur lors de l'appel a l'API Gemini (configuration, reseau, quota, reponse invalide). */
public class GeminiException extends RuntimeException {

    public GeminiException(String message) {
        super(message);
    }

    public GeminiException(String message, Throwable cause) {
        super(message, cause);
    }
}