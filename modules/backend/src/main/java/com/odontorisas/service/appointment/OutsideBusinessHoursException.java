package com.odontorisas.service.appointment;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** La cita no cae completa dentro de un tramo del horario de atención (422). */
public class OutsideBusinessHoursException extends BusinessException {

    public OutsideBusinessHoursException(boolean closedDay) {
        super(closedDay
            ? "La clínica no atiende ese día."
            : "La cita queda fuera del horario de atención.", HttpStatus.UNPROCESSABLE_CONTENT, "outside-business-hours");
    }
}
