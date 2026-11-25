package com.odontorisas.service.appointment;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** Choque en la agenda del odontólogo (409), por pre-chequeo o por la restricción de exclusión (design.md D2). */
public class AppointmentConflictException extends BusinessException {

    public AppointmentConflictException() {
        super("El odontólogo ya tiene otra cita en ese horario.", HttpStatus.CONFLICT, "appointment-conflict");
    }
}
