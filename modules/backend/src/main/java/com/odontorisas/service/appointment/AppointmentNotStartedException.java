package com.odontorisas.service.appointment;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** Marcar asistencia o atender con nota antes del inicio de la cita (409). */
public class AppointmentNotStartedException extends BusinessException {

    public AppointmentNotStartedException() {
        super("La cita aún no empezó.", HttpStatus.CONFLICT, "appointment-not-started");
    }
}
