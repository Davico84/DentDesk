package com.odontorisas.service.dentalchart;

import com.odontorisas.persistence.entity.DentalChartBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * El checklist mínimo que el sellado del odontograma exige (design.md D5, ADR-0017 D6). Puro y
 * estático a propósito — mismo patrón que {@code ClinicalHistoryChecklist}: no depende de Spring
 * ni de la base de datos, se puede testear como una función.
 *
 * <p>Deliberadamente <b>no</b> exige un número mínimo de hallazgos: una boca sana es un
 * odontograma legítimo y vacío de patología.
 */
final class DentalChartChecklist {

    private DentalChartChecklist() {
    }

    /** Campos que faltan para poder sellar; vacío si el checklist está satisfecho. */
    static List<String> missingFields(DentalChartBlock block) {
        List<String> missing = new ArrayList<>();
        if (!block.isSpecificationsReviewed()) {
            missing.add("specificationsReviewed");
        }
        if (!block.isObservationsReviewed()) {
            missing.add("observationsReviewed");
        }
        return missing;
    }
}
