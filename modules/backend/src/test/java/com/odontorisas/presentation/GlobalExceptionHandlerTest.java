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
        // update-api-type-contracts (D4): confirma que el 403 de method-security (@PreAuthorize)
        // ya salía en español — no era parte del hueco, pero el requirement lo cubre explícitamente.
        assertThat(problem.getDetail()).isEqualTo("No tienes permiso para realizar esta acción.");
    }

    @Test
    void uncaught_exception_maps_to_500() {
        ProblemDetail problem = handler.handleUncaught(new RuntimeException("boom"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    /**
     * update-api-type-contracts: el mapa {@code detailParaStatus} cubre en español los statuses
     * que resuelve el manejador por defecto de Spring MVC. Un status fuera del mapa (aquí, uno que
     * a propósito no está mapeado) cae en el genérico — nunca en el texto interno de Spring, y sin
     * romper por un status inesperado.
     */
    @Test
    void mapped_status_returns_its_own_spanish_detail() {
        assertThat(GlobalExceptionHandler.detailParaStatus(HttpStatus.BAD_REQUEST)).isEqualTo("La petición no es válida.");
        assertThat(GlobalExceptionHandler.detailParaStatus(HttpStatus.SERVICE_UNAVAILABLE))
            .isEqualTo("El servicio no está disponible temporalmente. Inténtalo de nuevo en unos minutos.");
    }

    @Test
    void unmapped_status_falls_back_to_the_generic_spanish_detail() {
        // CONFLICT no está en el mapa: no lo resuelve el manejador por defecto (BusinessException
        // / DataIntegrityViolationException ya lo cubren con su propio detail curado).
        assertThat(GlobalExceptionHandler.detailParaStatus(HttpStatus.CONFLICT))
            .isEqualTo("No se pudo procesar la petición.");
    }
}
