package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** La fecha y hora de la atención no puede ser futura ni anterior a la última nota sellada del paciente (design.md D4). */
public class InvalidAttendedAtException extends BusinessException {
    public InvalidAttendedAtException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "invalid-attended-at");
    }

    public static InvalidAttendedAtException future() {
        return new InvalidAttendedAtException("La fecha y hora de la atención no puede ser futura.");
    }

    public static InvalidAttendedAtException beforeLastSealed() {
        return new InvalidAttendedAtException("La fecha de la atención no puede ser anterior a la de la última nota sellada del paciente.");
    }
}
