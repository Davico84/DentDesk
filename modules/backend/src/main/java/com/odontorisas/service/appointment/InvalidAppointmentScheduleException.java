package com.odontorisas.service.appointment;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Inicio fuera de la grilla de 5 minutos en hora de la clínica, o duración inválida (400). Lleva el campo,
 * como la validación de Bean Validation, para que el cliente lo marque (add-appointments design.md D3).
 */
public class InvalidAppointmentScheduleException extends BusinessException {

    private final String field;

    public InvalidAppointmentScheduleException(String field, String message) {
        super(message, HttpStatus.BAD_REQUEST, "validation-error");
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
