package com.odontorisas.infra.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Construye la cookie del refresh token: {@code HttpOnly} + {@code Secure} +
 * {@code SameSite=Lax}, acotada a la base de la API + {@code /auth} ({@code /api/auth}). El valor
 * nunca es accesible por JavaScript.
 *
 * <p>El path incluye el {@code server.servlet.context-path} (add-cloud-deployment D1): el navegador
 * compara el path de la cookie con la URL real, y una cookie {@code Path=/auth} no viajaría a
 * {@code /api/auth/refresh}. La cookie de alta y la de borrado salen del mismo builder.
 */
@Component
public class RefreshCookieFactory {

    private final SecurityProperties props;
    private final String cookiePath;

    public RefreshCookieFactory(SecurityProperties props,
                                @Value("${server.servlet.context-path:}") String contextPath) {
        this.props = props;
        this.cookiePath = cookiePath(contextPath);
    }

    /** Base de la API normalizada + {@code /auth}: vacía → {@code /auth}; {@code /api} o {@code /api/} → {@code /api/auth}. */
    static String cookiePath(String contextPath) {
        String base = contextPath == null ? "" : contextPath.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.isEmpty() && !base.startsWith("/")) {
            base = "/" + base;
        }
        return base + "/auth";
    }

    public String cookieName() {
        return props.cookie().refreshName();
    }

    /** Cookie con el refresh token y su vida útil hasta {@code expiresAt}. */
    public ResponseCookie create(String value, Instant expiresAt) {
        long maxAgeSeconds = Math.max(0, Duration.between(Instant.now(), expiresAt).getSeconds());
        return baseBuilder(value).maxAge(maxAgeSeconds).build();
    }

    /** Cookie de borrado (maxAge=0) para el logout. */
    public ResponseCookie clear() {
        return baseBuilder("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String value) {
        return ResponseCookie.from(props.cookie().refreshName(), value)
            .httpOnly(true)
            .secure(props.cookie().secure())
            .sameSite("Lax")
            .path(cookiePath);
    }
}
