package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Marca un hallazgo del odontograma como atendido en la nota (update-consultation-note-findings
 * design.md D2): la descripción la compone la UI con las etiquetas del hallazgo y queda guardada.
 */
public record AttendFindingRequest(
    @NotNull
    @Schema(description = "El hallazgo del odontograma del paciente.")
    Long findingId,
    @NotBlank
    @Size(max = 300)
    @Schema(description = "Pieza, cara, hallazgo y estado, como se ven al marcarlo; queda fija en la nota.")
    String description) {
}
