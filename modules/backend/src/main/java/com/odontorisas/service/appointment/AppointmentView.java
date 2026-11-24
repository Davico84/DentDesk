package com.odontorisas.service.appointment;

import com.odontorisas.common.AppointmentStatus;
import com.odontorisas.persistence.entity.Appointment;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Una cita con los nombres de paciente y odontólogo resueltos (add-appointments design.md D8). */
public record AppointmentView(
    Long id,
    Long patientId,
    String patientName,
    Long dentistUserId,
    String dentistName,
    Instant startsAt,
    Instant endsAt,
    int durationMinutes,
    AppointmentStatus status,
    String reason,
    String cancellationReason,
    Long consultationNoteId,
    Instant statusChangedAt,
    Long statusChangedBy,
    String statusChangedByName) {

    static AppointmentView from(Appointment a, Map<Long, String> patientNames, Map<Long, String> userNames) {
        return new AppointmentView(
            a.getId(),
            a.getPatientId(),
            patientNames.get(a.getPatientId()),
            a.getDentistUserId(),
            userNames.get(a.getDentistUserId()),
            a.getStartsAt(),
            a.getEndsAt(),
            (int) Duration.between(a.getStartsAt(), a.getEndsAt()).toMinutes(),
            a.getStatus(),
            a.getReason(),
            a.getCancellationReason(),
            a.getConsultationNoteId(),
            a.getStatusChangedAt(),
            a.getStatusChangedBy(),
            a.getStatusChangedBy() == null ? null : userNames.get(a.getStatusChangedBy()));
    }
}
