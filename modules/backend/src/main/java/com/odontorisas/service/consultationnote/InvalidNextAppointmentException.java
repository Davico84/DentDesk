package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** La próxima cita es una fecha posterior al día de la atención (design.md D3). */
public class InvalidNextAppointmentException extends BusinessException {
    public InvalidNextAppointmentException() {
        super("La próxima cita debe ser posterior a la fecha de la atención.", HttpStatus.BAD_REQUEST, "invalid-next-appointment");
    }
}
