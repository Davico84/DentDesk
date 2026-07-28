package com.odontorisas.service.patient;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * El documento ya está registrado en <b>esta</b> clínica (409 Conflict). La unicidad es por
 * clínica (ADR-0004) e incondicional (design.md D1): si el documento pertenece a un paciente
 * dado de baja, sigue ocupado — la salida es reactivar su ficha, no crear otra.
 *
 * <p>Lleva el {@code existingPatientId} de la ficha que colisiona para que el cliente pueda
 * <b>ofrecer abrirla</b> (update-patients D3/D4). Se expone como extensión del error en un
 * {@code @ExceptionHandler} dedicado (no por {@code handleBusiness}, que solo traduce
 * {@code message/status/errorType}).
 */
public class DocumentAlreadyExistsException extends BusinessException {

    private final Long existingPatientId;

    public DocumentAlreadyExistsException(Long existingPatientId) {
        super("El documento ya está registrado en esta clínica.", HttpStatus.CONFLICT, "patient-document-taken");
        this.existingPatientId = existingPatientId;
    }

    public Long getExistingPatientId() {
        return existingPatientId;
    }
}
