package com.odontorisas.common;

/**
 * Organización activa (organizationId) de la petición en curso, en un {@link ThreadLocal}.
 * Alimenta tanto al resolver de tenant de Hibernate ({@code @TenantId}) como a la variable
 * de sesión {@code app.organization_id} de la Row-Level Security de negocio. Se puebla
 * EXCLUSIVAMENTE desde el token firmado y debe limpiarse al terminar la petición.
 */
public final class OrganizationContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private OrganizationContext() {
    }

    public static void set(Long organizationId) {
        CURRENT.set(organizationId);
    }

    /** Organización activa, o {@code null} si no hay ninguna establecida. */
    public static Long get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
