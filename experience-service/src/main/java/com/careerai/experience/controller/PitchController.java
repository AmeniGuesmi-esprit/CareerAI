package com.careerai.experience.controller;

import com.careerai.experience.dto.ApiResponse;
import com.careerai.experience.dto.PitchRequest;
import com.careerai.experience.dto.PitchResponse;
import com.careerai.experience.exception.GeminiException;
import com.careerai.experience.exception.PitchException;
import com.careerai.experience.exception.RateLimitException;
import com.careerai.experience.security.AuthUser;
import com.careerai.experience.service.PitchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/pitch")
@RequiredArgsConstructor
public class PitchController {

    private final PitchService pitchService;

    /** Genere 3 versions de pitch (courte / standard / enthousiaste) a partir de MES experiences. */
    @PostMapping
    public ResponseEntity<ApiResponse<PitchResponse>> generate(@AuthenticationPrincipal AuthUser user,
                                                               @Valid @RequestBody PitchRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Pitch genere", pitchService.generate(user.id(), request)));
    }

    // ---------- gestion des erreurs ----------

    /** 400 : validation du body (@Valid). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .collect(Collectors.joining(" ; "));
        return problem(HttpStatus.BAD_REQUEST, detail.isBlank() ? "Requete invalide" : detail);
    }

    /** 400 : JSON absent ou malforme. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, "Corps de la requete invalide ou JSON malforme");
    }

    /** 400 : entree invalide detectee par le service (longueur, aucune experience exploitable). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadInput(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** 429 : plus de 10 requetes par minute. */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ProblemDetail> handleRateLimit(RateLimitException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage()));
    }

    /** 502 : sortie Gemini inexploitable ou pitch non fiable apres retry. */
    @ExceptionHandler(PitchException.class)
    public ResponseEntity<ProblemDetail> handlePitch(PitchException e) {
        log.error("Pitch : echec de generation : {}", e.getMessage(), e);
        return problem(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    /**
     * Erreur de l'appel Gemini (GeminiClient enveloppe toutes ses erreurs dans GeminiException).
     * 503 : injoignable/timeout ou quota (429) ; 502 : autre erreur HTTP, reponse vide/bloquee, cle absente.
     * Le message de l'exception est journalise mais jamais renvoye au client.
     */
    @ExceptionHandler(GeminiException.class)
    public ResponseEntity<ProblemDetail> handleGemini(GeminiException e) {
        log.error("Pitch : erreur Gemini : {}", e.getMessage(), e);
        Throwable cause = e.getCause();
        if (isNetworkFailure(cause)) {
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "Le service d'IA est temporairement indisponible");
        }
        if (cause instanceof RestClientResponseException http && http.getStatusCode().value() == 429) {
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "Le service d'IA est temporairement surcharge");
        }
        return problem(HttpStatus.BAD_GATEWAY, "Erreur du service d'IA");
    }

    /** 503 : Gemini injoignable (timeout, reseau). */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ProblemDetail> handleGeminiUnreachable(ResourceAccessException e) {
        log.error("Pitch : Gemini injoignable", e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Le service d'IA est temporairement indisponible");
    }

    /** 503 si quota Gemini atteint (429 amont), 502 pour les autres erreurs HTTP de Gemini. */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ProblemDetail> handleGeminiHttpError(RestClientResponseException e) {
        log.error("Pitch : erreur Gemini HTTP {}", e.getStatusCode().value(), e);
        if (e.getStatusCode().value() == 429) {
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "Le service d'IA est temporairement surcharge");
        }
        return problem(HttpStatus.BAD_GATEWAY, "Erreur du service d'IA");
    }

    /** 502 : autre erreur cliente vers Gemini. */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ProblemDetail> handleGeminiOther(RestClientException e) {
        log.error("Pitch : erreur Gemini", e);
        return problem(HttpStatus.BAD_GATEWAY, "Erreur du service d'IA");
    }

    /**
     * Vrai si la chaine de causes contient une panne reseau : connexion refusee/coupee, timeout de lecture.
     * Spring enveloppe les timeouts survenus pendant la lecture du corps dans une RestClientException
     * (et non dans une ResourceAccessException) : tester la cause directe ne suffit donc pas.
     */
    private static boolean isNetworkFailure(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ResourceAccessException || t instanceof java.net.SocketException
                    || t instanceof java.net.SocketTimeoutException || t instanceof java.io.EOFException) {
                return true;
            }
        }
        return false;
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}