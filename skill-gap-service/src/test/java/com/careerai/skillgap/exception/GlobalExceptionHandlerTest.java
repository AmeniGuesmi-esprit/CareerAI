package com.careerai.skillgap.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.assertj.core.api.Assertions.assertThat;

/** Une panne de MySQL est signalée clairement (503) au lieu d'une « Erreur interne du serveur ». */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void lostDatabaseConnectionGives503WithAClearMessage() {
        var res = handler.handleDatabase(new DataAccessResourceFailureException("Communications link failure"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(res.getBody().getMessage()).contains("Base de données indisponible", "MySQL");
    }

    @Test
    void failedRollbackAndUnavailableTransactionAreAlsoDatabaseErrors() {
        assertThat(handler.handleDatabase(new JpaSystemException(new RuntimeException("Unable to rollback")))
                .getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(handler.handleDatabase(new CannotCreateTransactionException("Connection refused"))
                .getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void otherUnexpectedErrorsStayInternal() {
        var res = handler.handleOther(new IllegalStateException("bug"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody().getMessage()).isEqualTo("Erreur interne du serveur");
    }
}
