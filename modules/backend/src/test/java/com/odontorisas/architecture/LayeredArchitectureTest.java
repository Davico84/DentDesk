package com.odontorisas.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

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

    /**
     * Solo código de producción: las reglas de capas describen la <b>arquitectura</b>, no los tests.
     * Un IT de {@code presentation} que siembra datos con un repositorio es legítimo, y sin esto
     * se contaría como violación.
     */
    private final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.odontorisas");

    /**
     * <b>El guardián del guardián.</b> Estas reglas estuvieron verdes durante meses <b>sin
     * comprobar nada</b>: ArchUnit 1.3.0 no sabía leer el bytecode de Java 25
     * (<em>class file major 69</em>), fallaba en silencio, {@code classes} quedaba vacío y el
     * {@code allowEmptyShould(true)} las daba por buenas. Al arreglarlo aparecieron violaciones
     * reales que llevaban ahí desde el principio.
     *
     * <p>Una red de seguridad no puede <b>parecer verde porque está vacía</b>. Este test lo impide.
     */
    @Test
    void the_importer_actually_reads_the_bytecode() {
        assertThat(classes).isNotEmpty();
        assertThat(classes.stream().map(JavaClass::getSimpleName))
            .as("si el importador estuviera roto, las reglas de capas pasarían sin comprobar nada")
            .contains("BrandingService", "BrandingController");
    }

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
