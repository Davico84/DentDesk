package com.odontorisas.testsupport;

/**
 * Alias de la función {@code set_config} de PostgreSQL para los slices de test con H2
 * (que no la implementa). Sin esto, el {@code SELECT set_config(...)} que fija el contexto
 * de tenant rompería toda transacción en H2.
 *
 * <p>Es un <em>no-op</em>: H2 no aplica Row-Level Security, así que solo evita el fallo de
 * la sentencia; el filtrado de aplicación ({@code @TenantId}) sí se ejercita en H2, y la
 * barrera de motor (RLS) se prueba únicamente con PostgreSQL real (Testcontainers).
 *
 * <p>Se registra con {@code CREATE ALIAS SET_CONFIG FOR
 * "com.odontorisas.testsupport.H2Functions.setConfig"} (ver la configuración de los slices).
 */
public final class H2Functions {

    private H2Functions() {
    }

    @SuppressWarnings("unused") // invocada por H2 vía alias
    public static String setConfig(String name, String value, boolean local) {
        return value;
    }
}
