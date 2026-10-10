package com.odontorisas.presentation.dto;

import com.odontorisas.common.PatientStatus;
import com.odontorisas.service.patient.PatientSummary;

import java.time.LocalDate;

/** Fila del listado de pacientes (la tabla de recepción). La ficha completa es {@link PatientResponse}. */
public record PatientSummaryResponse(
    Long id,
    String fullName,
    String document,
    LocalDate birthDate,
    boolean minor,
    String phone,
    PatientStatus status) {

    public static PatientSummaryResponse from(PatientSummary s) {
        return new PatientSummaryResponse(
            s.id(), s.fullName(), s.document(), s.birthDate(), s.minor(), s.phone(), s.status());
    }
}
