package com.careerai.competence.exception;

import org.springframework.http.HttpStatus;

public class AiException extends RuntimeException {
    private final HttpStatus status;

    public AiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
