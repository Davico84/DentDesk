package com.odontorisas.service.appointment;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** La cita está en un estado final (409): no se reprograma ni cambia de estado (design.md D6). */
public class AppointmentClosedException extends BusinessException {

    public AppointmentClosedException() {
        super("La cita ya está cerrada; no se puede modificar.", HttpStatus.CONFLICT, "appointment-closed");
    }
}
