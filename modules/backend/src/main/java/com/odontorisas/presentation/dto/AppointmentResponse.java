package com.odontorisas.presentation.dto;

import com.odontorisas.common.AppointmentStatus;
import com.odontorisas.service.appointment.AppointmentView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Una cita con los nombres de paciente y odontólogo (add-appointments design.md D8). Inicio y fin son instantes
 * UTC: se muestran en la zona horaria de la clínica ({@code GET /business-hours}).
 */
public record AppointmentResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long patientId,
    String patientName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long dentistUserId,
    String dentistName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Inicio (UTC)") Instant startsAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Fin (UTC)") Instant endsAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int durationMinutes,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AppointmentStatus status,
    String reason,
    String cancellationReason,
    @Schema(description = "Nota de consulta abierta desde la cita, si la hay") Long consultationNoteId,
    @Schema(description = "Cuándo cambió de estado (ausente si sigue AGENDADA)") Instant statusChangedAt,
    Long statusChangedBy,
    String statusChangedByName) {

    public static AppointmentResponse from(AppointmentView v) {
        return new AppointmentResponse(v.id(), v.patientId(), v.patientName(), v.dentistUserId(), v.dentistName(),
            v.startsAt(), v.endsAt(), v.durationMinutes(), v.status(), v.reason(), v.cancellationReason(),
            v.consultationNoteId(), v.statusChangedAt(), v.statusChangedBy(), v.statusChangedByName());
    }
}
