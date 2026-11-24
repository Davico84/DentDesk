package com.odontorisas.presentation.dto;

import com.odontorisas.service.appointment.AppointmentService.ScheduleData;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Agendar una cita (add-appointments). El inicio es un instante; que caiga en múltiplo de 5 minutos de la hora
 * de la clínica, que sea futuro y que entre en el horario lo valida el servicio con la zona de la clínica.
 */
public record ScheduleAppointmentRequest(
    @NotNull
    @Schema(example = "42")
    Long patientId,

    @NotNull
    @Schema(example = "7", description = "Usuario odontólogo: miembro con capacidad clínica vigente en la fecha de la cita")
    Long dentistUserId,

    @NotNull
    @Schema(example = "2030-12-03T15:00:00Z", description = "Instante de inicio (UTC). Mostrar y elegir en la zona "
        + "horaria de la clínica (GET /business-hours), nunca en la del dispositivo")
    Instant startsAt,

    @NotNull
    @Min(value = 10, message = "La duración mínima es 10 minutos")
    @Max(value = 240, message = "La duración máxima es 240 minutos")
    @Schema(example = "30", description = "Minutos, múltiplo de 5 entre 10 y 240")
    Integer durationMinutes,

    @Size(max = 500, message = "El motivo admite hasta 500 caracteres")
    @Schema(example = "Control")
    String reason) {

    public ScheduleData toData() {
        return new ScheduleData(patientId, dentistUserId, startsAt, durationMinutes, reason);
    }
}
