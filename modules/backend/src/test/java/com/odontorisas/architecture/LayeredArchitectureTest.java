package com.odontorisas.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Scenario "Capas del backend" (spec project-foundation): la lógica de negocio
 * vive en {@code service.<dominio>} (fuera de {@code infra}); {@code infra} son
 * adaptadores técnicos. Reglas de dependencia:
 * <ul>
 *   <li>{@code presentation} no depende de {@code persistence} (habla con {@code service}).</li>
 *   <li>{@code persistence} no depende de {@code presentation} ni de {@code service} (modelo limpio).</li>
 *   <li>{@code service} no depende de {@code presentation}.</li>
 * </ul>
 * No se restringe {@code infra → service}: la configuración/seguridad es el
 * composition root y cablea servicios legítimamente.
 */
class LayeredArchitectureTest {

    private final JavaClasses classes =
        new ClassFileImporter().importPackages("com.odontorisas");

    @Test
    void presentation_should_not_depend_on_persistence() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..presentation..")
            .should().dependOnClassesThat().resideInAPackage("..persistence..")
            .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void persistence_should_not_depend_on_presentation_or_service() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..persistence..")
            .should().dependOnClassesThat().resideInAnyPackage("..presentation..", "..service..")
            .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void service_should_not_depend_on_presentation() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..presentation..")
            .allowEmptyShould(true);
        rule.check(classes);
    }
}
