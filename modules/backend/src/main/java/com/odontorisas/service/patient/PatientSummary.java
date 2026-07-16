package com.odontorisas.service.patient;

import com.odontorisas.common.PatientStatus;
import com.odontorisas.persistence.entity.Patient;

import java.time.LocalDate;

/**
 * Fila del listado de pacientes: lo justo para la tabla de recepción (el uso diario). La ficha
 * completa se obtiene al consultar un paciente ({@link PatientView}).
 */
public record PatientSummary(
    Long id,
    String fullName,
    String document,
    LocalDate birthDate,
    boolean minor,
    String phone,
    PatientStatus status) {

    public static PatientSummary from(Patient p) {
        return new PatientSummary(
            p.getId(), p.getFullName(), p.getDocument(), p.getBirthDate(), p.isMinor(),
            p.getPhone(), p.getStatus());
    }
}
