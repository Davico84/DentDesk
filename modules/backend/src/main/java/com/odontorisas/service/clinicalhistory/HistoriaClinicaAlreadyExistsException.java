package com.odontorisas.service.clinicalhistory;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * El paciente ya tiene una Historia Clínica en esta clínica (409 Conflict) — solo se permite una
 * por paciente, de por vida (ADR-0011). Se lanza tanto por el pre-chequeo como por la violación
 * de {@code UNIQUE(patient_id)} bajo una creación concurrente (design.md, spec: creación
 * concurrente) — la unicidad la garantiza el dato, el pre-chequeo es solo la vía feliz.
 */
public class HistoriaClinicaAlreadyExistsException extends BusinessException {
    public HistoriaClinicaAlreadyExistsException() {
        super("El paciente ya tiene una Historia Clínica en esta clínica.",
            HttpStatus.CONFLICT, "clinical-history-already-exists");
    }
}
