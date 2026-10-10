package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** Gate de la nota de consulta (design.md D1): solo con la Historia Clínica {@code COMPLETED}. */
public class ClinicalHistoryNotCompletedException extends BusinessException {
    public ClinicalHistoryNotCompletedException() {
        super("Primero debe completarse la Historia Clínica del paciente.", HttpStatus.CONFLICT, "clinical-history-not-completed");
    }
}
