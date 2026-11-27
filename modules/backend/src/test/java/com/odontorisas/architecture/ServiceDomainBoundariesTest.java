package com.odontorisas.architecture;

import com.odontorisas.service.auth.AuthService;
import com.odontorisas.service.staff.StaffService;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dependencias entre dominios del backend (update-module-boundaries, ADR-0021): los paquetes
 * {@code service.<dominio>} no forman ciclos y cada uno depende solo de los dominios que declara
 * {@link #ALLOWED}. Es el grafo real: las aristas están aceptadas, no son un ideal. Una arista nueva =
 * una entrada aquí, justificada en el PR.
 *
 * <p>Vigila <b>dominios</b>: las clases sueltas en {@code service} ({@code BusinessException}) y las
 * capas {@code presentation}/{@code infra} quedan fuera. Las reglas reciben el {@link JavaClasses} a
 * evaluar, para poder verlas fallar contra fixtures de {@code src/test}.
 */
class ServiceDomainBoundariesTest {

    /** Dominio → dominios de los que puede depender (además de sí mismo). */
    static final Map<String, Set<String>> ALLOWED = Map.of(
        "admission", Set.of("clinicalhistory", "dentalchart", "staff"),
        "appointment", Set.of("consultationnote", "patient", "staff"),
        "auth", Set.of("organization", "staff"),
        "clinicalhistory", Set.of("patient"),
        "consultationnote", Set.of("clinicalhistory", "patient", "staff"),
        "dentalchart", Set.of("patient"),
        "organization", Set.of(),
        "patient", Set.of(),
        "platform", Set.of("auth", "organization", "staff"),
        "staff", Set.of());

    private static final Pattern DOMAIN = Pattern.compile("^com\\.odontorisas\\.service\\.([a-z0-9_]+)(\\..*)?$");

    /** Solo producción, como las demás reglas de arquitectura. */
    private final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.odontorisas");

    // ---- reglas: reciben las clases a evaluar ----

    static void checkNoCycles(JavaClasses evaluated) {
        slices().matching("com.odontorisas.service.(*)..").should().beFreeOfCycles().check(evaluated);
    }

    /** Un dominio presente sin entrada en el mapa no puede quedar fuera de la regla: falla. */
    static void checkEveryDomainDeclared(JavaClasses evaluated) {
        Set<String> undeclared = new TreeSet<>(domainsIn(evaluated));
        undeclared.removeAll(ALLOWED.keySet());
        if (!undeclared.isEmpty()) {
            throw new AssertionError("Paquetes service.<dominio> sin entrada en ServiceDomainBoundariesTest.ALLOWED "
                + "(declararlos con sus dependencias permitidas): " + undeclared);
        }
    }

    /** Los dominios se toman de las clases evaluadas, no de una lista fija (un dominio nuevo no escapa). */
    static void checkDeclaredDependencies(JavaClasses evaluated) {
        checkEveryDomainDeclared(evaluated);
        Set<String> known = new TreeSet<>(ALLOWED.keySet());
        known.addAll(domainsIn(evaluated));
        for (String domain : domainsIn(evaluated)) {
            String[] forbidden = known.stream()
                .filter(other -> !other.equals(domain) && !ALLOWED.get(domain).contains(other))
                .map(other -> "com.odontorisas.service." + other + "..")
                .toArray(String[]::new);
            if (forbidden.length == 0) {
                continue;
            }
            noClasses().that().resideInAPackage("com.odontorisas.service." + domain + "..")
                .should().dependOnClassesThat().resideInAnyPackage(forbidden)
                .as("service." + domain + " solo depende de los dominios que declara ALLOWED: " + ALLOWED.get(domain))
                .check(evaluated);
        }
    }

    static Set<String> domainsIn(JavaClasses evaluated) {
        Set<String> domains = new TreeSet<>();
        for (JavaClass c : evaluated) {
            Matcher m = DOMAIN.matcher(c.getPackageName());
            if (m.matches()) {
                domains.add(m.group(1));
            }
        }
        return domains;
    }

    // ---- sobre el código de producción ----

    /** Guardián del importador: debe traer los dos extremos del ciclo que se rompió, no solo "algo". */
    @Test
    void the_importer_actually_reads_the_bytecode() {
        assertThat(classes.stream().map(JavaClass::getPackageName))
            .as("si el importador estuviera roto, las reglas de dominios pasarían sin comprobar nada")
            .contains("com.odontorisas.service.auth", "com.odontorisas.service.staff");
        assertThat(domainsIn(classes)).hasSizeGreaterThanOrEqualTo(ALLOWED.size());
    }

    @Test
    void service_domains_are_free_of_cycles() {
        checkNoCycles(classes);
    }

    @Test
    void auth_depends_on_staff_but_staff_no_longer_depends_on_auth() {
        assertThat(dependsOn(classes, "auth", "staff")).isTrue();
        assertThat(dependsOn(classes, "staff", "auth")).isFalse();
    }

    @Test
    void every_service_domain_has_an_entry_in_the_map() {
        checkEveryDomainDeclared(classes);
    }

    @Test
    void every_service_domain_depends_only_on_what_it_declares() {
        checkDeclaredDependencies(classes);
    }

    // ---- se ven fallar con fixtures de src/test (docs/testing.md §5) ----

    @Test
    void a_cycle_between_domains_fails_naming_both() throws Exception {
        JavaClasses withCycle = new ClassFileImporter().importClasses(
            fixture("com.odontorisas.service.staff.StaffDependsOnAuthFixture"), AuthService.class, StaffService.class);
        assertThatThrownBy(() -> checkNoCycles(withCycle))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("auth")
            .hasMessageContaining("staff");
    }

    @Test
    void an_undeclared_dependency_fails_naming_both_packages() throws Exception {
        JavaClasses undeclared = new ClassFileImporter().importClasses(
            fixture("com.odontorisas.service.patient.PatientDependsOnStaffFixture"), StaffService.class);
        assertThatThrownBy(() -> checkDeclaredDependencies(undeclared))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("service.patient")
            .hasMessageContaining("service.staff");
    }

    @Test
    void a_domain_without_entry_in_the_map_fails() throws Exception {
        JavaClasses newDomain = new ClassFileImporter().importClasses(
            fixture("com.odontorisas.service.zzundeclared.UndeclaredDomainFixture"));
        assertThatThrownBy(() -> checkEveryDomainDeclared(newDomain))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("zzundeclared");
    }

    /** Los fixtures son package-private en su propio paquete: se cargan por nombre. */
    private static Class<?> fixture(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    private static boolean dependsOn(JavaClasses evaluated, String from, String to) {
        String target = "com.odontorisas.service." + to;
        return evaluated.stream()
            .filter(c -> c.getPackageName().startsWith("com.odontorisas.service." + from))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .map(Dependency::getTargetClass)
            .anyMatch(t -> t.getPackageName().equals(target) || t.getPackageName().startsWith(target + "."));
    }
}
