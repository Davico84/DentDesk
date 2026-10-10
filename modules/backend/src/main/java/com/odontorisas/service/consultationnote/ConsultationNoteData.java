package com.odontorisas.service.consultationnote;

import java.time.Instant;
import java.time.LocalDate;

/**
 * El contenido editable de una nota abierta, en el lenguaje de la capa de servicio (design.md D3/D4).
 * Guardar reemplaza todos los campos: un {@code null} borra el campo, igual que el guardado de la
 * Historia en {@code DRAFT}.
 */
public record ConsultationNoteData(
    Instant attendedAt,
    String reason,
    String illnessDuration,
    String symptomsAndSigns,
    String previousTreatment,
    String biologicalFunctions,
    String examination,
    String treatment,
    String auxiliaryExams,
    String referral,
    LocalDate nextAppointmentOn) {
}
