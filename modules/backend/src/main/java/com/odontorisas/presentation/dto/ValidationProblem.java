package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Schema del cuerpo de un {@code 400}. Es {@link ApiProblem} <b>más</b> la extensión
 * {@code errors}, que detalla qué campo falló y por qué — lo que permite al frontend pintar el
 * mensaje <b>junto a cada campo</b> en vez de en un aviso genérico.
 *
 * <p><b>{@code errors} es opcional</b> a propósito: un {@code 400} tiene <b>dos formas</b> y este
 * schema debe describir ambas con fidelidad:
 * <ul>
 *   <li><b>validación</b> ({@code @Valid} falla) → lleva {@code errors};</li>
 *   <li><b>JSON malformado</b> (lo resuelve la clase padre del handler) → <b>no</b> lo lleva.</li>
 * </ul>
 *
 * <p>Repite los campos de {@link ApiProblem} en vez de heredarlos (los records no admiten
 * herencia) para que el tipo generado en el cliente sea <b>plano</b> y directo de consumir.
 */
@Schema(name = "ValidationProblem",
    description = "Error 400 en formato RFC 9457. Si viene de la validación, incluye el detalle por campo.")
public record ValidationProblem(

    @Schema(description = "Identificador del tipo de error.", example = "/errors/validation-error")
    String type,

    @Schema(description = "URI de la petición que produjo el error.", example = "/auth/register")
    String instance,

    @Schema(description = "Resumen legible del tipo de error.", example = "Validation failed")
    String title,

    @Schema(description = "Código de estado HTTP.", example = "400")
    Integer status,

    @Schema(description = "Explicación concreta del error.", example = "Uno o más campos son inválidos.")
    String detail,

    @Schema(description = "Momento en que se produjo el error.", example = "2026-07-12T10:15:30Z")
    Instant timestamp,

    @Schema(description = "Identificador de traza. Opcional: solo si la petición lo tiene.",
        example = "3f2a1b4c5d6e7f80", nullable = true)
    String traceId,

    @Schema(description = "Campos inválidos. Ausente si el 400 no proviene de la validación "
        + "(p. ej. JSON malformado).", nullable = true)
    List<FieldError> errors) {

    /** Un campo inválido y el motivo, para mostrarlo junto al campo del formulario. */
    @Schema(name = "FieldError", description = "Campo inválido y su motivo.")
    public record FieldError(

        @Schema(description = "Nombre del campo del request que falló.", example = "password")
        String field,

        @Schema(description = "Motivo, apto para mostrar al usuario.",
            example = "La contraseña debe tener al menos 8 caracteres")
        String message) {
    }
}
