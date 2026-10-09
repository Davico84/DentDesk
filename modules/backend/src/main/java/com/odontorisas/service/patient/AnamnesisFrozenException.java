package com.odontorisas.service.patient;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * La anamnesis de la ficha no cambia desde que el paciente tiene Historia Clínica
 * (`update-clinical-history` design.md D10, ADR-0020 D6): lo nuevo se registra en la consulta.
 */
public class AnamnesisFrozenException extends BusinessException {
    public AnamnesisFrozenException() {
        super("El paciente ya tiene Historia Clínica: los antecedentes nuevos se registran en la consulta.",
            HttpStatus.CONFLICT, "patient-anamnesis-frozen");
    }
}
