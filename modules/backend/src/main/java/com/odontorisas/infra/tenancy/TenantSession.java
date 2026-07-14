package com.odontorisas.infra.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * Fija las variables de sesión de PostgreSQL que consume la Row-Level Security, sobre la
 * conexión de la transacción en curso. Las variables son {@code transaction-local}
 * ({@code set_config(..., true)}), así que se revierten al {@code COMMIT}/{@code ROLLBACK}
 * y no contaminan la conexión al volver al pool.
 *
 * <p><b>No la llames desde el código de negocio.</b> El contexto de tenant se fija
 * <em>solo</em> en cada transacción ({@link TenantAwareTransactionManager}). Una regla de
 * ArchUnit ({@code MultiTenancyArchitectureTest}) <b>rompe la build</b> si una clase de
 * {@code service} (salvo {@code service.auth}) o de {@code presentation} depende de ella:
 * el aislamiento debe ser <em>por defecto, no por disciplina</em>.
 *
 * <p>La única excepción son los flujos de autenticación: en el registro la organización se
 * crea <em>dentro de esa misma transacción</em>, así que no hay token del que leer el contexto
 * y no hay nada que propagar. Ahí {@link #bindIdentity(long)} se llama explícitamente.
 *
 * <p><b>Por qué las variantes estáticas reciben el {@link EntityManager} explícito:</b> son las
 * que usa el enganche automático, y este NO puede usar el {@code @PersistenceContext} de abajo.
 * Es un proxy que resuelve el {@code EntityManager} ligado al hilo; invocado antes de que la
 * transacción quede enlazada, abriría <em>otro</em> {@code EntityManager} sobre <em>otra</em>
 * conexión — y el {@code set_config}, al ser transaction-local, se fijaría en la conexión
 * equivocada y se descartaría con ella. La transacción real nunca vería la variable.
 */
@Component
public class TenantSession {

    private static final String SET_IDENTITY =
            "SELECT set_config('app.user_id', :userId, true)";

    private static final String SET_IDENTITY_AND_ORGANIZATION =
            "SELECT set_config('app.user_id', :userId, true), "
                    + "set_config('app.organization_id', :organizationId, true)";

    @PersistenceContext
    private EntityManager entityManager;

    /** Fija {@code app.user_id} (RLS de identidad sobre memberships). */
    public void bindIdentity(long userId) {
        bindIdentity(entityManager, userId);
    }

    /** Fija identidad y organización en una sola sentencia (RLS de identidad + de negocio). */
    public void bind(long userId, long organizationId) {
        bind(entityManager, userId, organizationId);
    }

    /** Para el enganche automático: fija la identidad sobre la conexión de SU transacción. */
    static void bindIdentity(EntityManager em, long userId) {
        em.createNativeQuery(SET_IDENTITY)
                .setParameter("userId", Long.toString(userId))
                .getSingleResult();
    }

    /** Para el enganche automático: fija ambas sobre la conexión de SU transacción. */
    static void bind(EntityManager em, long userId, long organizationId) {
        em.createNativeQuery(SET_IDENTITY_AND_ORGANIZATION)
                .setParameter("userId", Long.toString(userId))
                .setParameter("organizationId", Long.toString(organizationId))
                .getSingleResult();
    }
}
