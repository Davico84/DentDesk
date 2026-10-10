package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** No se abre una nota de consulta a un paciente dado de baja (decisión 2026-10-09, design.md D1). */
public class PatientInactiveException extends BusinessException {
    public PatientInactiveException() {
        super("El paciente está dado de baja; primero debe reactivarse.", HttpStatus.CONFLICT, "patient-inactive");
    }
}
