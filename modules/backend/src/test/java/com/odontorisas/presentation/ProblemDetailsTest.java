package com.odontorisas.presentation;

import com.odontorisas.infra.config.TraceIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fija la forma del cuerpo de error de la API (Scenario "El schema de error describe el cuerpo
 * real"). Estas aserciones son el contrato que los schemas {@code ApiProblem}/{@code ValidationProblem}
 * documentan: si alguien cambia la fábrica, el contrato dejaría de decir la verdad y este test rompe.
 *
 * <p>Corre en surefire (sin Docker), así que la fidelidad del error se verifica también en local.
 */
class ProblemDetailsTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void builds_an_rfc9457_problem_with_type_title_status_and_detail() {
        ProblemDetail problem = ProblemDetails.of(HttpStatus.CONFLICT, "email-already-exists", "Ya existe.");

        assertThat(problem.getType()).hasToString("/errors/email-already-exists");
        assertThat(problem.getTitle()).isEqualTo(HttpStatus.CONFLICT.getReasonPhrase());
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("Ya existe.");
    }

    /** {@code timestamp} SIEMPRE se emite → en el contrato va como campo presente. */
    @Test
    void always_emits_timestamp() {
        ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, "validation-error", "Inválido.");

        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    /** {@code traceId} solo si el MDC lo tiene → en el contrato debe ir como OPCIONAL, no requerido. */
    @Test
    void emits_trace_id_only_when_present_in_the_mdc() {
        MDC.put(TraceIdFilter.TRACE_ID_KEY, "trace-123");
        ProblemDetail withTrace = ProblemDetails.of(HttpStatus.BAD_REQUEST, "validation-error", "Inválido.");
        assertThat(withTrace.getProperties()).containsEntry("traceId", "trace-123");

        MDC.clear();
        ProblemDetail withoutTrace = ProblemDetails.of(HttpStatus.BAD_REQUEST, "validation-error", "Inválido.");
        assertThat(withoutTrace.getProperties()).doesNotContainKey("traceId");
    }

    /**
     * ⚠️ <b>Cuidado con lo que este test NO prueba.</b> La fábrica no fija {@code instance}… pero la
     * API <b>sí lo emite</b>: Spring lo rellena con la URI de la petición <b>al renderizar</b>. Es
     * decir, este test pasa y aun así {@code instance} aparece en la respuesta.
     *
     * <p>Me costó un fallo en CI: concluí "la API nunca emite {@code instance}" leyendo el código
     * (nadie llama a {@code setInstance}) y dejé el campo fuera del schema. Solo comparar el schema
     * con la <b>respuesta real</b> lo destapó ({@code ErrorContractIT}).
     *
     * <p><b>Moraleja</b>: la autoridad sobre la forma del cuerpo es el test que mira la respuesta
     * de la API, no este. Este solo cubre lo que la fábrica pone <em>de su parte</em>.
     */
    @Test
    void the_factory_itself_does_not_set_instance_but_spring_adds_it_when_rendering() {
        ProblemDetail problem = ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal-server-error", "Ups.");

        assertThat(problem.getInstance()).isNull();
    }
}
