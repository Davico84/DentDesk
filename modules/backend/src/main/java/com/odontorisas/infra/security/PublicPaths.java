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

    /**
     * Patrones de ruta accesibles sin autenticar. Bajo {@code /auth} es una <b>lista explícita</b>, no
     * {@code /auth/**}: ahí vive también el cambio de contraseña, que exige sesión
     * ({@code update-authentication-my-profile} design.md D1), y un endpoint nuevo bajo {@code /auth} no debe
     * nacer público por omisión.
     */
    public static final String[] PATTERNS = {
        "/auth/login", "/auth/register", "/auth/refresh", "/auth/logout", "/auth/invitations/**",
        "/platform/auth/login",
        // Solo health: /actuator/info queda autenticado (add-cold-start-warmup D2), nada lo usa.
        "/actuator/health/**",
        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private PublicPaths() {
    }

    /** {@code true} si la ruta es pública (y por tanto no puede fallar con un 401 de sesión ausente). */
    public static boolean isPublic(String path) {
        if (path == null) {
            return false;
        }
        for (String pattern : PATTERNS) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
