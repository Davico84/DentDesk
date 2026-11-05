package com.odontorisas.architecture;

import com.odontorisas.common.SignerSnapshot;
import com.odontorisas.service.admission.CloseAdmissionService;
import com.odontorisas.service.dentalchart.DentalChartService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 2.8e (design.md D4, ADR-0012 D5): {@code DentalChartService.sealInitial()} solo lo invoca
 * el coordinador {@link CloseAdmissionService}. Es {@code public} porque el coordinador vive en
 * otro paquete, así que el compilador no lo impide; si otra clase lo llamara, el odontograma
 * podría sellarse sin la Historia Clínica y caería la invariante de sellado atómico. Esta regla
 * es la que sostiene esa restricción.
 */
class AdmissionSealingArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.odontorisas");

    private static final ArchRule ONLY_THE_COORDINATOR_SEALS_THE_DENTAL_CHART = noClasses()
        .that().doNotHaveFullyQualifiedName(CloseAdmissionService.class.getName())
        .should().callMethod(DentalChartService.class, "sealInitial", Long.class, SignerSnapshot.class)
        .because("sellar el odontograma sin la Historia rompe el sellado atómico (ADR-0012 D5)");

    @Test
    void only_the_admission_coordinator_calls_seal_initial() {
        ONLY_THE_COORDINATOR_SEALS_THE_DENTAL_CHART.check(classes);
    }

    /** Que la regla no pase por estar vacía: el coordinador sí debe llamarlo. */
    @Test
    void the_coordinator_does_call_seal_initial() {
        assertThat(classes.get(CloseAdmissionService.class).getMethodCallsFromSelf())
            .anySatisfy(call -> {
                assertThat(call.getTargetOwner().isEquivalentTo(DentalChartService.class)).isTrue();
                assertThat(call.getName()).isEqualTo("sealInitial");
            });
    }

    /** Que la regla detecte de verdad una llamada desde otra clase. */
    @Test
    void the_rule_catches_a_caller_other_than_the_coordinator() {
        JavaClasses withIntruder = new ClassFileImporter().importClasses(
            IntruderThatSealsAlone.class, DentalChartService.class, CloseAdmissionService.class);

        assertThat(ONLY_THE_COORDINATOR_SEALS_THE_DENTAL_CHART.evaluate(withIntruder).hasViolation()).isTrue();
    }

    @SuppressWarnings("unused")
    private static final class IntruderThatSealsAlone {
        void sealWithoutClinicalHistory(DentalChartService service) {
            service.sealInitial(1L, null);
        }
    }
}
