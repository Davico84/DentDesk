package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** Solo se descarta una nota abierta vacía (design.md D2): lo escrito en una atención es registro clínico aunque no se haya sellado. */
public class ConsultationNoteNotEmptyException extends BusinessException {
    public ConsultationNoteNotEmptyException() {
        super("La nota tiene contenido; no se puede descartar.", HttpStatus.CONFLICT, "consultation-note-not-empty");
    }
}
