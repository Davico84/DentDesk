package com.odontorisas.service.dentalchart;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * El checklist mínimo del odontograma no está satisfecho (400 Bad Request) — no se puede sellar
 * (design.md D5, spec: rechazar el sellado sin especificaciones/observaciones revisadas). Lleva
 * {@code missingFields}, mismo patrón que {@code ChecklistIncompleteException} de clinical-history.
 */
public class DentalChartChecklistIncompleteException extends BusinessException {

    private final List<String> missingFields;

    public DentalChartChecklistIncompleteException(List<String> missingFields) {
        super("Faltan datos para sellar el odontograma.", HttpStatus.BAD_REQUEST,
            "dental-chart-checklist-incomplete");
        this.missingFields = missingFields;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }
}
