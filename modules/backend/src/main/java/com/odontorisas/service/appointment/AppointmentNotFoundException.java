package com.odontorisas.service.appointment;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** Cita inexistente o de otra clínica (404): no se distinguen (add-appointments). */
public class AppointmentNotFoundException extends BusinessException {

    public AppointmentNotFoundException() {
        super("La cita no existe.", HttpStatus.NOT_FOUND, "appointment-not-found");
    }
}
