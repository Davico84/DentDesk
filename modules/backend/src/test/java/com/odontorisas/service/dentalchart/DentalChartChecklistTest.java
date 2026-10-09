package com.odontorisas.service.dentalchart;

import com.odontorisas.common.FindingType;
import com.odontorisas.persistence.entity.DentalChartBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checklist de sellado del odontograma como función pura (design.md D5/D6). Cubre el scenario
 * "Rechazar el sellado si un tipo que exige especificación no la tiene" y la decisión de que la
 * movilidad NO la exige (6.1.19: el grado va en la sigla).
 */
class DentalChartChecklistTest {

    private static DentalChartBlock reviewed(String specifications) {
        return DentalChartBlock.builder()
            .specifications(specifications)
            .specificationsReviewed(true)
            .observationsReviewed(true)
            .build();
    }

    @Test
    void with_both_sections_reviewed_and_no_finding_requiring_specification_it_is_complete() {
        assertThat(DentalChartChecklist.missingFields(reviewed(null), List.of(FindingType.GIROVERSION))).isEmpty();
    }

    @Test
    void a_crown_with_empty_specifications_is_rejected_even_if_marked_reviewed() {
        assertThat(DentalChartChecklist.missingFields(reviewed(null), List.of(FindingType.CORONA)))
            .containsExactly(DentalChartChecklist.SPECIFICATIONS_REQUIRED);
        assertThat(DentalChartChecklist.missingFields(reviewed("   "), List.of(FindingType.CORONA)))
            .containsExactly(DentalChartChecklist.SPECIFICATIONS_REQUIRED);
    }

    @Test
    void a_crown_with_the_detail_written_is_complete() {
        assertThat(DentalChartChecklist.missingFields(reviewed("Pieza 16: corona metálica dorada"),
            List.of(FindingType.CORONA))).isEmpty();
    }

    @Test
    void temporary_crown_and_fluorosis_also_require_it() {
        assertThat(DentalChartChecklist.missingFields(reviewed(null), List.of(FindingType.CORONA_TEMPORAL)))
            .contains(DentalChartChecklist.SPECIFICATIONS_REQUIRED);
        assertThat(DentalChartChecklist.missingFields(reviewed(null), List.of(FindingType.DEFECTOS_DESARROLLO_ESMALTE)))
            .contains(DentalChartChecklist.SPECIFICATIONS_REQUIRED);
    }

    @Test
    void mobility_does_not_require_it_because_its_grade_goes_in_the_acronym() {
        assertThat(DentalChartChecklist.missingFields(reviewed(null), List.of(FindingType.MOVILIDAD_PATOLOGICA))).isEmpty();
    }

    @Test
    void unreviewed_sections_are_still_reported() {
        DentalChartBlock block = DentalChartBlock.builder().build();
        assertThat(DentalChartChecklist.missingFields(block, List.of()))
            .containsExactly("specificationsReviewed", "observationsReviewed");
    }
}
