package com.odontorisas.service.appointment;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** Agendar o reprogramar con un inicio que ya pasó (422). */
public class AppointmentInPastException extends BusinessException {

    public AppointmentInPastException() {
        super("La cita debe ser futura.", HttpStatus.UNPROCESSABLE_CONTENT, "appointment-in-past");
    }
}
