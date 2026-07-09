package com.odontorisas.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * Reglas de arquitectura de la capacidad multi-tenancy.
 *
 * <ul>
 *   <li><b>Regla transaccional (D12):</b> el acceso a entidades bajo Row-Level Security
 *       ({@code memberships} y futuras de negocio) debe ocurrir en una transacción, porque
 *       las variables {@code set_config(..., true)} son transaction-local. Se comprueba de
 *       forma proxy: los servicios que dependen de {@code MembershipRepository} deben estar
 *       anotados con {@code @Transactional} a nivel de clase.</li>
 *   <li><b>Tripwire async (D13):</b> hoy no existe propagación de contexto a hilos async.
 *       Este test FALLA la build si alguien introduce {@code @Async}/{@code @Scheduled}
 *       antes de cablear el {@code TenantContextTaskDecorator} (ver docs/backend.md), para
 *       evitar pérdidas silenciosas del contexto de tenant en segundo plano.</li>
 * </ul>
 */
class MultiTenancyArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.odontorisas");

    @Test
    void rls_access_from_services_must_be_transactional() {
        ArchRule rule = classes()
                .that().resideInAPackage("..service..")
                .and().dependOnClassesThat().haveSimpleName("MembershipRepository")
                .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void async_must_not_be_used_until_tenant_context_is_propagated() {
        ArchRule noAsync = noMethods()
                .should().beAnnotatedWith("org.springframework.scheduling.annotation.Async")
                .orShould().beAnnotatedWith("org.springframework.scheduling.annotation.Scheduled")
                .because("un hilo @Async/@Scheduled no hereda IdentityContext/OrganizationContext "
                        + "(ThreadLocal); antes de introducirlo hay que cablear el "
                        + "TenantContextTaskDecorator (docs/backend.md, design.md D13)")
                .allowEmptyShould(true);
        noAsync.check(classes);
    }
}
