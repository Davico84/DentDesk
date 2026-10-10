package com.odontorisas.presentation.dto;

import com.odontorisas.common.NoteTextField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Corrección de un error material (DS 009-2017-SA art. 47 c, design.md D6): el campo es la
 * "ubicación del error". Solo campos de texto: los diagnósticos no se corrigen así.
 */
public record CorrectionRequest(
    @NotNull
    @Schema(description = "Campo de texto de la nota que se corrige.")
    NoteTextField field,
    @NotBlank
    @Schema(description = "El texto correcto.")
    String correctedText,
    @NotBlank
    @Schema(description = "Motivo de la corrección.")
    String reason) {
}
