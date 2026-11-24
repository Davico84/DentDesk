package com.odontorisas.presentation.dto;

import com.odontorisas.service.appointment.AppointmentService.RescheduleData;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Reprogramar una cita agendada: odontólogo, inicio y duración, con las mismas reglas que al agendar. */
public record RescheduleAppointmentRequest(
    @NotNull
    @Schema(example = "7")
    Long dentistUserId,

    @NotNull
    @Schema(example = "2030-12-05T21:00:00Z", description = "Instante de inicio (UTC), elegido en la zona de la clínica")
    Instant startsAt,

    @NotNull
    @Min(value = 10, message = "La duración mínima es 10 minutos")
    @Max(value = 240, message = "La duración máxima es 240 minutos")
    @Schema(example = "30", description = "Minutos, múltiplo de 5 entre 10 y 240")
    Integer durationMinutes) {

    public RescheduleData toData() {
        return new RescheduleData(dentistUserId, startsAt, durationMinutes);
    }
}
