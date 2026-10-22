package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** El hallazgo ya está marcado en la misma nota (design.md D1). */
public class NoteFindingAlreadyAttendedException extends BusinessException {
    public NoteFindingAlreadyAttendedException() {
        super("El hallazgo ya está marcado como atendido en esta nota.", HttpStatus.CONFLICT, "consultation-note-finding-already-attended");
    }
}
