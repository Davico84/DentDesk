package com.odontorisas.infra.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * Fija las variables de sesión de PostgreSQL que consume la Row-Level Security, sobre la
 * conexión de la transacción en curso. Debe invocarse DENTRO de una transacción activa
 * (el {@code EntityManager} usa la conexión ligada a ella); las variables son
 * {@code transaction-local} ({@code set_config(..., true)}), así que se revierten al
 * {@code COMMIT}/{@code ROLLBACK} y no contaminan la conexión al volver al pool.
 *
 * <p><b>Alcance de este change.</b> La única tabla con RLS es {@code memberships}
 * (aislada por identidad) y solo se toca dentro de los flujos de autenticación, que
 * fijan la identidad explícitamente con {@link #bindIdentity(long)}. La propagación
 * <em>automática</em> por transacción para las tablas de negocio (Open Question 1 del
 * diseño) se finaliza cuando aterrice la primera capacidad de negocio y pueda validarse
 * con sus tests de RLS; su punto de enganche vivirá aquí.
 */
@Component
public class TenantSession {

    @PersistenceContext
    private EntityManager entityManager;

    /** Fija {@code app.user_id} (RLS de identidad sobre memberships). */
    public void bindIdentity(long userId) {
        entityManager.createNativeQuery("SELECT set_config('app.user_id', :userId, true)")
                .setParameter("userId", Long.toString(userId))
                .getSingleResult();
    }

    /** Fija identidad y organización en una sola sentencia (RLS de identidad + de negocio). */
    public void bind(long userId, long organizationId) {
        entityManager.createNativeQuery(
                        "SELECT set_config('app.user_id', :userId, true), "
                                + "set_config('app.organization_id', :organizationId, true)")
                .setParameter("userId", Long.toString(userId))
                .setParameter("organizationId", Long.toString(organizationId))
                .getSingleResult();
    }
}
