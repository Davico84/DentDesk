package com.odontorisas.infra.security;

import org.springframework.util.AntPathMatcher;

/**
 * Fuente <b>única</b> de las rutas públicas (sin sesión).
 *
 * <p>La consumen dos sitios que deben coincidir siempre:
 * <ul>
 *   <li>{@link SecurityConfig}, para autorizarlas sin autenticación;</li>
 *   <li>el customizer de OpenAPI, para decidir a qué operaciones <b>no</b> añadir un {@code 401}.</li>
 * </ul>
 *
 * <p>Si la lista se duplicara, el contrato acabaría documentando un {@code 401} en operaciones
 * donde nunca ocurre (o callándolo donde sí). De ahí que viva en un solo lugar.
 */
public final class PublicPaths {

    /** Patrones de ruta accesibles sin autenticar. */
    public static final String[] PATTERNS = {
        "/auth/**",
        "/platform/auth/login",
        // Solo health: /actuator/info queda autenticado (add-cold-start-warmup D2), nada lo usa.
        "/actuator/health/**",
        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    /**
     * Rutas que caen bajo un patrón público pero <b>exigen sesión</b>. Hoy solo el cambio de contraseña, que
     * vive bajo {@code /auth} porque ahí viaja la cookie de refresco ({@code update-authentication-my-profile}
     * design.md D1). {@link SecurityConfig} las declara autenticadas <b>antes</b> de los patrones públicos.
     */
    public static final String[] AUTHENTICATED = {
        "/auth/password"
    };

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private PublicPaths() {
    }

    /** {@code true} si la ruta es pública (y por tanto no puede fallar con un 401 de sesión ausente). */
    public static boolean isPublic(String path) {
        if (path == null) {
            return false;
        }
        for (String pattern : AUTHENTICATED) {
            if (MATCHER.match(pattern, path)) {
                return false;
            }
        }
        for (String pattern : PATTERNS) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
