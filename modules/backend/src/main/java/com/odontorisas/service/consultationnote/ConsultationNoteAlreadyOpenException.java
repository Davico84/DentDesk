package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** El paciente ya tiene una nota {@code ABIERTA} (design.md D1/D2): hay que sellarla o descartarla antes de abrir otra. */
public class ConsultationNoteAlreadyOpenException extends BusinessException {
    public ConsultationNoteAlreadyOpenException() {
        super("El paciente ya tiene una nota de consulta sin sellar.", HttpStatus.CONFLICT, "consultation-note-already-open");
    }
}
