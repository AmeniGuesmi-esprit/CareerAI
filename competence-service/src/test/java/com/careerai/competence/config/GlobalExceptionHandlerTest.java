package com.careerai.competence.config;

import com.careerai.competence.dto.ApiResponse;
import com.careerai.competence.exception.AiException;
import com.careerai.competence.exception.BadRequestException;
import com.careerai.competence.exception.ConflictException;
import com.careerai.competence.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static void assertError(ResponseEntity<ApiResponse<Void>> res, HttpStatus status, String message) {
        assertThat(res.getStatusCode()).isEqualTo(status);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().isSuccess()).isFalse();
        assertThat(res.getBody().getMessage()).isEqualTo(message);
        assertThat(res.getBody().getData()).isNull();
    }

    @Test
    void badRequest_400() {
        assertError(handler.handleBadRequest(new BadRequestException("oups")), HttpStatus.BAD_REQUEST, "oups");
    }

    @Test
    void notFound_404() {
        assertError(handler.handleNotFound(new ResourceNotFoundException("absent")), HttpStatus.NOT_FOUND, "absent");
    }

    @Test
    void conflict_409_usesExceptionMessage() {
        assertError(handler.handleConflict(new ConflictException("doublon")), HttpStatus.CONFLICT, "doublon");
    }

    @Test
    void dataIntegrity_409_genericMessage() {
        assertError(handler.handleConflict(new DataIntegrityViolationException("sql")),
                HttpStatus.CONFLICT, "Cette donnée existe déjà");
    }

    @Test
    void aiException_usesItsOwnStatus() {
        assertError(handler.handleAi(new AiException(HttpStatus.TOO_MANY_REQUESTS, "quota")),
                HttpStatus.TOO_MANY_REQUESTS, "quota");
    }

    @Test
    void unreadableBody_400() {
        assertError(handler.handleUnreadable(mock(HttpMessageNotReadableException.class)),
                HttpStatus.BAD_REQUEST, "Corps de requête invalide");
    }

    @Test
    void validation_400_firstFieldMessage() throws Exception {
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "req");
        br.addError(new FieldError("req", "nom", "Le nom est obligatoire"));
        br.addError(new FieldError("req", "niveau", "Autre"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(mock(MethodParameter.class), br);

        assertError(handler.handleValidation(ex), HttpStatus.BAD_REQUEST, "Le nom est obligatoire");
    }

    @Test
    void validation_noFieldErrors_defaultMessage() throws Exception {
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(mock(MethodParameter.class),
                new BeanPropertyBindingResult(new Object(), "req"));

        assertError(handler.handleValidation(ex), HttpStatus.BAD_REQUEST, "Données invalides");
    }
}
