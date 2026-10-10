package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Falta contenido obligatorio para sellar la nota (400, design.md D3): motivo, examen, tratamiento
 * o al menos un diagnóstico. Lleva {@code missingFields}, mismo patrón que
 * {@code ChecklistIncompleteException} de la Historia.
 */
public class ConsultationNoteChecklistIncompleteException extends BusinessException {

    private final List<String> missingFields;

    public ConsultationNoteChecklistIncompleteException(List<String> missingFields) {
        super("Faltan datos para sellar la nota de consulta.", HttpStatus.BAD_REQUEST, "consultation-note-incomplete");
        this.missingFields = missingFields;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }
}
