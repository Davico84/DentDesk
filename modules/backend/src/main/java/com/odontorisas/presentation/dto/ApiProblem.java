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
 * <p>Fidelidad (la fija {@code ErrorContractIT} comparando contra el cuerpo <b>real</b>):
 * {@code traceId} es <b>opcional</b> (solo si el MDC lo tiene) e {@code instance} <b>sí se emite</b>,
 * aunque el handler nunca llame a {@code setInstance}: <b>Spring lo rellena con la URI de la
 * petición al renderizar</b>. Se descubrió al comparar el schema con la respuesta real — leer el
 * código no bastaba.
 */
@Schema(name = "ApiProblem", description = "Error de la API en formato RFC 9457 (ProblemDetail).")
public record ApiProblem(

    @Schema(description = "Identificador del tipo de error; el cliente ramifica por él.",
        example = "/errors/email-already-exists")
    String type,

    @Schema(description = "URI de la petición que produjo el error.", example = "/auth/register")
    String instance,

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
    String traceId,

    @Schema(description = "Id de la ficha existente que provoca el conflicto. "
        + "Opcional: solo se emite en el error 'patient-document-taken', para ofrecer abrirla.",
        example = "42", nullable = true)
    Long existingPatientId) {
}
