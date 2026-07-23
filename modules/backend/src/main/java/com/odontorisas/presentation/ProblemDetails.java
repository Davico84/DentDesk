package com.odontorisas.presentation;

import com.odontorisas.infra.config.TraceIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;

/**
 * Fábrica única del cuerpo de error de la API (RFC 9457).
 *
 * <p><b>Toda</b> respuesta de error sale de aquí —las de {@link GlobalExceptionHandler} y la de
 * "sesión ausente" que el entry point de seguridad delega en él—, para que la API falle
 * <b>siempre con la misma forma</b>. Esa uniformidad es lo que permite que el contrato OpenAPI
 * la describa con un <b>único schema</b> ({@code ApiProblem} / {@code ValidationProblem}) y que
 * el frontend derive sus tipos de error en vez de escribirlos a mano.
 *
 * <p>Extensiones sobre RFC 9457: {@code timestamp} (siempre) y {@code traceId} (solo si el MDC lo
 * tiene, ver {@link TraceIdFilter}) — de ahí que en el contrato {@code traceId} sea <b>opcional</b>.
 * {@code instance} SÍ se emite: Spring lo rellena solo con la URI de la petición al renderizar el
 * {@link ProblemDetail}, aunque ningún código de este proyecto llame a {@code setInstance(...)}.
 * Verificado contra la respuesta real, no solo leyendo el código — ver {@code ErrorContractIT}.
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    /** Problema estándar: {@code type} = {@code /errors/<errorType>}, título = razón del status. */
    public static ProblemDetail of(HttpStatus status, String errorType, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("/errors/" + errorType));
        problem.setTitle(status.getReasonPhrase());
        addExtensions(problem);
        return problem;
    }

    /** Añade las extensiones comunes a un {@link ProblemDetail} ya construido. */
    public static void addExtensions(ProblemDetail problem) {
        problem.setProperty("timestamp", Instant.now());
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
    }
}
