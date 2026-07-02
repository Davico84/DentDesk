package com.odontorisas.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Scenario "Capas del backend": existen presentation, infra y persistence, y
 * presentation no importa directamente de persistence (flujo presentation -> infra -> persistence).
 */
class LayeredArchitectureTest {

    private final JavaClasses classes =
        new ClassFileImporter().importPackages("com.odontorisas");

    @Test
    void presentation_should_not_depend_on_persistence() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..presentation..")
            .should().dependOnClassesThat().resideInAPackage("..persistence..")
            // Scaffolding: aún no hay clases de presentación (solo package-info).
            .allowEmptyShould(true);
        rule.check(classes);
    }
}
