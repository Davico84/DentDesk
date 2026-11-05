package com.odontorisas.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.EntityListeners;
import org.hibernate.annotations.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

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

    /**
     * <b>El guardián del guardián.</b> Si el importador no puede leer el bytecode (p. ej. tras
     * subir de versión de Java: ArchUnit 1.3.0 no entendía el <em>class file major 69</em> y
     * fallaba <em>en silencio</em>), {@code classes} queda <b>vacío</b> y todas las reglas de
     * abajo pasan en verde <b>sin comprobar nada</b> — las salva el {@code allowEmptyShould(true)},
     * que existe para reglas legítimamente vacías, no para un importador roto.
     *
     * <p>Es exactamente el fallo que una red de seguridad no puede permitirse: <b>parecer verde
     * porque está vacía</b>. Este test lo hace imposible.
     */
    @Test
    void the_importer_actually_reads_the_bytecode() {
        assertThat(classes).isNotEmpty();
        assertThat(classes.stream().map(JavaClass::getSimpleName))
                .as("si el importador estuviera roto, las demás reglas pasarían sin comprobar nada")
                .contains("TenantSession", "TenantAwareTransactionManager");
    }

    @Test
    void rls_access_from_services_must_be_transactional() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service..")
                .and().areNotAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                .should().dependOnClassesThat().haveSimpleName("MembershipRepository")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    /**
     * El riesgo real de este mecanismo no es que falle: es que <b>alguien lo desactive sin
     * querer</b>. El día que RLS bloquee una consulta por falta de contexto, la reacción natural
     * será <em>"pues fijo el contexto aquí y ya"</em> — y el aislamiento vuelve a depender de que
     * nadie se olvide, <b>sin que nadie se entere</b>.
     *
     * <p>Un párrafo en {@code docs/backend.md} no lo impide. Esta regla sí: <b>rompe la build</b>.
     *
     * <p>Única excepción, {@code service.auth}: en el registro la organización se crea <em>dentro
     * de esa misma transacción</em>, así que no hay token del que leer el contexto y no hay nada
     * que propagar. No es una grieta en la regla — es que en el bootstrap no existe el contexto.
     */
    @Test
    void business_code_must_not_bind_the_tenant_context_by_hand() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..service..", "..presentation..")
                .and().resideOutsideOfPackage("..service.auth..")
                .should().dependOnClassesThat().haveSimpleName("TenantSession")
                .because("el contexto de tenant se fija SOLO, en TenantAwareTransactionManager. "
                        + "Una capacidad de negocio queda aislada sin escribir código de "
                        + "aislamiento: por defecto, no por disciplina (docs/backend.md §8.1)")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    private static final DescribedPredicate<JavaClass> DECLARES_TENANT_ID = DescribedPredicate.describe(
            "declara un campo @TenantId",
            javaClass -> javaClass.getFields().stream()
                    .anyMatch(field -> field.isAnnotatedWith(TenantId.class)));

    /**
     * <b>Autoría automática (docs/backend.md §8.6, design.md D5 de {@code update-multi-tenancy}
     * / D4 de {@code update-patients}).</b> Toda entidad de negocio bajo RLS registra quién la
     * creó y quién la modificó por última vez — mismo criterio que el aislamiento: <em>por
     * defecto, no por disciplina</em>. Sin este candado, declarar {@code @TenantId} sin la
     * autoría correspondiente no falla: dos columnas se quedan en {@code NULL} en silencio.
     */
    @Test
    void business_entities_with_tenant_id_must_declare_authorship() {
        ArchRule rule = classes()
                .that(DECLARES_TENANT_ID)
                .should(new ArchCondition<JavaClass>("declarar @EntityListeners(AuditingEntityListener.class) "
                        + "y los campos @CreatedBy/@LastModifiedBy") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        boolean hasListener = item.isAnnotatedWith(EntityListeners.class);
                        boolean hasCreatedBy = item.getFields().stream()
                                .anyMatch(f -> f.isAnnotatedWith(CreatedBy.class));
                        boolean hasLastModifiedBy = item.getFields().stream()
                                .anyMatch(f -> f.isAnnotatedWith(LastModifiedBy.class));
                        if (!hasListener || !hasCreatedBy || !hasLastModifiedBy) {
                            events.add(SimpleConditionEvent.violated(item, item.getFullName()
                                    + " tiene @TenantId pero no declara autoría completa "
                                    + "(@EntityListeners + @CreatedBy + @LastModifiedBy, docs/backend.md §8.6)"));
                        }
                    }
                })
                .allowEmptyShould(true);
        rule.check(classes);
    }

    /**
     * <b>Lo que el tripwire protege, en las tareas que sí corren fuera de la petición</b>
     * ({@code update-authentication-password-recovery} design.md D4). El pedido de recuperación y el aviso de
     * cambio de contraseña corren en executors propios, sin contexto de tenant, y solo tocan tablas sin RLS.
     * Si una de estas clases empezara a leer o fijar el contexto, el día que lo necesitara lo leería vacío o
     * heredado de otra petición. La limpieza de los {@code ThreadLocal} vive en el executor
     * ({@code AccountTaskExecutors}), no en las tareas.
     */
    @Test
    void account_background_tasks_do_not_touch_the_tenant_context() {
        assertThat(classes.stream().map(JavaClass::getSimpleName))
                .as("si se renombran, esta regla dejaría de vigilar algo")
                .contains("PasswordRecoveryWorker", "PasswordChangeNotifier", "SmtpAccountMailer");
        ArchRule rule = noClasses()
                .that().haveSimpleName("PasswordRecoveryWorker")
                .or().haveSimpleName("PasswordChangeNotifier")
                .or().resideInAPackage("..infra.mail..")
                .should().dependOnClassesThat().haveSimpleName("IdentityContext")
                .orShould().dependOnClassesThat().haveSimpleName("OrganizationContext")
                .orShould().dependOnClassesThat().haveSimpleName("TenantSession")
                .because("corren fuera de la petición, sin contexto de tenant (design.md D4)");
        rule.check(classes);
    }

    /** Los correos no leen la base: reciben todo armado, así que no pueden tocar una tabla con RLS (D4). */
    @Test
    void mail_adapters_do_not_depend_on_persistence() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..infra.mail..")
                .should().dependOnClassesThat().resideInAPackage("..persistence..");
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
