package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Guardado de una nota abierta (design.md D3): reemplaza todos los campos. Sin {@code @NotBlank} en
 * el contenido: el borrador acepta campos vacíos; el contenido obligatorio se exige al sellar.
 */
public record ConsultationNoteRequest(
    @NotNull
    @Schema(description = "Cuándo ocurrió la atención (ISO-8601). No futura ni anterior a la última nota sellada.")
    Instant attendedAt,
    @Schema(description = "Motivo de consulta. Obligatorio para sellar.")
    String reason,
    @Schema(description = "Tiempo de enfermedad.")
    String illnessDuration,
    @Schema(description = "Síntomas y signos más importantes.")
    String symptomsAndSigns,
    @Schema(description = "Tratamiento recibido, cumplimiento y resultado (de ser el caso).")
    String previousTreatment,
    @Schema(description = "Funciones biológicas.")
    String biologicalFunctions,
    @Schema(description = "Examen (evaluación odontológica). Obligatorio para sellar.")
    String examination,
    @Schema(description = "Tratamiento. Obligatorio para sellar.")
    String treatment,
    @Schema(description = "Exámenes auxiliares.")
    String auxiliaryExams,
    @Schema(description = "Referencia, si fuera el caso.")
    String referral,
    @Schema(description = "Fecha de próxima cita, posterior al día de la atención. Solo fecha.")
    LocalDate nextAppointmentOn) {
}
