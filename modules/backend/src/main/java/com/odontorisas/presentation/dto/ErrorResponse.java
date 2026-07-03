package com.odontorisas.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Cuerpo de error consistente. {@code fieldErrors} solo se incluye en errores de
 * validación (400): mapa campo → mensaje.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(int status, String error, String message, Map<String, String> fieldErrors) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, null);
    }
}
