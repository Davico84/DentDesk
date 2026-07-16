package com.odontorisas.service.patient;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * El documento ya está registrado en <b>esta</b> clínica (409 Conflict). La unicidad es por
 * clínica (ADR-0004) e incondicional (design.md D1): si el documento pertenece a un paciente
 * dado de baja, sigue ocupado — la salida es reactivar su ficha, no crear otra.
 */
public class DocumentAlreadyExistsException extends BusinessException {
    public DocumentAlreadyExistsException() {
        super("El documento ya está registrado en esta clínica.", HttpStatus.CONFLICT, "patient-document-taken");
    }
}
