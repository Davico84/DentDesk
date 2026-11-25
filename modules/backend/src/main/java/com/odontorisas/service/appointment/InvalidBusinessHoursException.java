package com.odontorisas.service.appointment;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

import java.time.DayOfWeek;

/** Horario de atención inválido (400): no se guarda ningún cambio (add-appointments design.md D5). */
public class InvalidBusinessHoursException extends BusinessException {

    public InvalidBusinessHoursException(DayOfWeek day, String problem) {
        super("Horario de atención inválido el " + DayNames.spanish(day) + ": " + problem + ".",
            HttpStatus.BAD_REQUEST, "invalid-business-hours");
    }
}
