package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** La corrección de error material es sobre una nota sellada (design.md D6); un borrador se edita. */
public class ConsultationNoteNotSealedException extends BusinessException {
    public ConsultationNoteNotSealedException() {
        super("Solo se corrige una nota sellada.", HttpStatus.CONFLICT, "consultation-note-not-sealed");
    }
}
