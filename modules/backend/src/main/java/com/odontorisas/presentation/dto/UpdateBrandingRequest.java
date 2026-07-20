package com.odontorisas.presentation.dto;

import com.odontorisas.common.BrandPreset;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * Actualización de la marca de la organización activa. Todos los campos son opcionales;
 * enviar {@code null} en uno restaura su valor por defecto. Si ambos quedan vacíos, el
 * servicio elimina la personalización. El color SHALL ser una clave del catálogo curado de
 * presets (update-organization-branding design.md D1/D2) — una clave fuera del catálogo no
 * deserializa y el 400 resultante lo maneja Jackson (mismo camino que un JSON malformado, ver
 * {@code docs/frontend.md §2.6}). La URL del logo SHALL ser {@code https} (se rechazan
 * {@code javascript:}, {@code data:} y {@code http:} — ver design.md D7 de add-organization-branding).
 */
public record UpdateBrandingRequest(
    @Schema(description = "Preset de marca del catálogo curado, o null para restaurar el default")
    BrandPreset brandPreset,

    @Pattern(regexp = "^https://[^\\s]+$", message = "La URL del logo debe usar https")
    @Schema(example = "https://cdn.example.com/clinica/logo.png")
    String logoUrl) {
}
