package com.odontorisas.service.clinicalhistory;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * El checklist mínimo de la Historia Clínica no está satisfecho (400 Bad Request) — no se puede
 * sellar a {@code COMPLETED} (design.md D4, spec: rechazar el sellado con un ítem crítico
 * faltante). Lleva {@code missingFields} para que el cliente indique qué falta, mismo patrón que
 * {@code existingPatientId} en `update-patients` D4: una excepción de negocio con datos extra no
 * pasa por {@code handleBusiness} genérico, necesita su propio {@code @ExceptionHandler}.
 */
public class ChecklistIncompleteException extends BusinessException {

    private final List<String> missingFields;

    public ChecklistIncompleteException(List<String> missingFields) {
        super("Faltan datos para completar la Historia Clínica.", HttpStatus.BAD_REQUEST, "clinical-history-incomplete");
        this.missingFields = missingFields;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }
}
