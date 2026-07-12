package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Schema del cuerpo de error estándar de la API (RFC 9457). Documenta lo que produce
 * {@code ProblemDetails}: es el <b>único</b> formato con el que falla la API, incluido el
 * {@code 401} de sesión ausente.
 *
 * <p>Existe como record propio (y no se deja que la herramienta derive el schema del
 * {@code ProblemDetail} de Spring) porque Spring guarda las extensiones en un mapa dinámico
 * que la herramienta <b>no ve</b>: el schema saldría sin {@code timestamp} ni {@code traceId},
 * y el frontend no podría tiparlos.
 *
 * <p>Fidelidad (fijada por {@code ProblemDetailsTest}): <b>no se documenta {@code instance}</b>
 * porque la API nunca lo emite, y {@code traceId} es <b>opcional</b> porque solo se envía si el
 * MDC lo tiene. Documentar de más sería volver a mentir en el contrato.
 */
@Schema(name = "ApiProblem", description = "Error de la API en formato RFC 9457 (ProblemDetail).")
public record ApiProblem(

    @Schema(description = "Identificador del tipo de error; el cliente ramifica por él.",
        example = "/errors/email-already-exists")
    String type,

    @Schema(description = "Resumen legible del tipo de error.", example = "Conflict")
    String title,

    @Schema(description = "Código de estado HTTP.", example = "409")
    Integer status,

    @Schema(description = "Explicación concreta de este error, apta para mostrar al usuario.",
        example = "El correo ya está registrado.")
    String detail,

    @Schema(description = "Momento en que se produjo el error.", example = "2026-07-12T10:15:30Z")
    Instant timestamp,

    @Schema(description = "Identificador de traza para correlacionar con los logs. "
        + "Opcional: solo se emite si la petición lo tiene.",
        example = "3f2a1b4c5d6e7f80", nullable = true)
    String traceId) {
}
