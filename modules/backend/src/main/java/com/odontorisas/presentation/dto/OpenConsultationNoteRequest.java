package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Apertura de una nota de consulta: solo la fecha y hora de la atención (design.md D4). */
public record OpenConsultationNoteRequest(
    @NotNull
    @Schema(description = "Cuándo ocurrió la atención (ISO-8601). No futura ni anterior a la última nota sellada.",
        example = "2026-10-09T15:30:00Z")
    Instant attendedAt) {
}
