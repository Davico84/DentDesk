package com.odontorisas.service.dentalchart;

import com.odontorisas.common.FindingType;
import com.odontorisas.persistence.entity.DentalChartBlock;

import java.util.ArrayList;
import java.util.Collection;
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

    /** Falta el texto de especificaciones que exige algún hallazgo registrado. */
    static final String SPECIFICATIONS_REQUIRED = "specifications";

    private DentalChartChecklist() {
    }

    /**
     * Campos que faltan para poder sellar; vacío si el checklist está satisfecho.
     *
     * <p>Si algún hallazgo es de un tipo que la norma obliga a detallar en especificaciones
     * ({@link FindingType#requiresSpecification()}: corona 6.1.3, corona temporal 6.1.4, fluorosis
     * 6.1.5), "revisado y sin nada que anotar" ya no alcanza: el texto tiene que existir. No se
     * puede comprobar <i>qué</i> dice — es texto libre —, solo que no esté vacío.
     */
    static List<String> missingFields(DentalChartBlock block, Collection<FindingType> findingTypes) {
        List<String> missing = new ArrayList<>();
        if (!block.isSpecificationsReviewed()) {
            missing.add("specificationsReviewed");
        }
        if (!block.isObservationsReviewed()) {
            missing.add("observationsReviewed");
        }
        boolean specificationRequired = findingTypes.stream().anyMatch(FindingType::requiresSpecification);
        if (specificationRequired && isBlank(block.getSpecifications())) {
            missing.add(SPECIFICATIONS_REQUIRED);
        }
        return missing;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
