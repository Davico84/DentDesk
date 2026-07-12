package com.odontorisas.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato del manejo de errores. En particular: una {@link AccessDeniedException} (la que lanza
 * {@code @PreAuthorize} al denegar) debe mapear a <b>403</b> y NO caer en el catch-all
 * {@code Exception} → 500. Este fue el fallo que solo aparecía en CI (el IT que lo cubría se salta
 * sin Docker); el unit test lo fija localmente para evitar la regresión.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void access_denied_maps_to_403_not_500() {
        ProblemDetail problem = handler.handleAccessDenied(new AccessDeniedException("denegado"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getType().toString()).contains("access-denied");
    }

    @Test
    void uncaught_exception_maps_to_500() {
        ProblemDetail problem = handler.handleUncaught(new RuntimeException("boom"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }
}
