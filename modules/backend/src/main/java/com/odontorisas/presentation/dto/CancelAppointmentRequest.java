package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Cancelar una cita agendada, con motivo opcional. */
public record CancelAppointmentRequest(
    @Size(max = 500, message = "El motivo admite hasta 500 caracteres")
    @Schema(example = "El paciente pidió otro día")
    String reason) {
}
