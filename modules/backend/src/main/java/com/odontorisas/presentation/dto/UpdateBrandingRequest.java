package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * Actualización de la marca de la organización activa. Todos los campos son opcionales;
 * enviar {@code null} en uno restaura su valor por defecto. Si los tres quedan vacíos, el
 * servicio elimina la personalización. La URL del logo SHALL ser {@code https} (se rechazan
 * {@code javascript:}, {@code data:} y {@code http:} — ver design.md D7).
 */
public record UpdateBrandingRequest(
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "El color debe ser un hex #RRGGBB")
    @Schema(example = "#7f1aa6")
    String brandPrimary,

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "El color debe ser un hex #RRGGBB")
    @Schema(example = "#16C3B2")
    String brandSecondary,

    @Pattern(regexp = "^https://[^\\s]+$", message = "La URL del logo debe usar https")
    @Schema(example = "https://cdn.example.com/clinica/logo.png")
    String logoUrl) {
}
