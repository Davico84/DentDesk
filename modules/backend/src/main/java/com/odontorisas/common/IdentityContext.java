package com.odontorisas.common;

/**
 * Identidad (userId) de la petición en curso, en un {@link ThreadLocal}. Alimenta la
 * variable de sesión {@code app.user_id} que usa la Row-Level Security de identidad
 * (memberships). Se puebla desde el token firmado (o explícitamente en el flujo de auth)
 * y SIEMPRE debe limpiarse al terminar la petición (un ThreadLocal sucio en un pool de
 * hilos es una vía de fuga).
 */
public final class IdentityContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private IdentityContext() {
    }

    public static void set(Long userId) {
        CURRENT.set(userId);
    }

    /** Identidad actual, o {@code null} si no hay ninguna establecida. */
    public static Long get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
