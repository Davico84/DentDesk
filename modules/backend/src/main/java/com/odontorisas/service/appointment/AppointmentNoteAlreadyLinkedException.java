package com.odontorisas.service.appointment;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** "Atender con nota" en una cita que ya tiene nota enlazada (409, design.md D7). */
public class AppointmentNoteAlreadyLinkedException extends BusinessException {

    public AppointmentNoteAlreadyLinkedException() {
        super("La cita ya tiene una nota de consulta enlazada.", HttpStatus.CONFLICT, "appointment-note-already-linked");
    }
}
