package com.odontorisas.infra.security;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuración del bloqueo por intentos fallidos de login (prefijo {@code app.auth.lockout},
 * design.md D6, update-authentication). Externalizada y validada: el arranque falla si
 * {@code maxAttempts < 1} o {@code window} no es positiva.
 */
@ConfigurationProperties(prefix = "app.auth.lockout")
@Validated
public record LoginLockoutProperties(@Min(1) int maxAttempts, Duration window) {

    public LoginLockoutProperties {
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("app.auth.lockout.window debe ser una duración positiva");
        }
    }
}
