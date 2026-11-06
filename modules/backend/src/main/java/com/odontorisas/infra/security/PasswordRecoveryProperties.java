package com.odontorisas.infra.security;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Recuperación de la contraseña (prefijo {@code app.password-recovery},
 * {@code update-authentication-password-recovery} design.md D2–D4, D10).
 *
 * @param linkTtl              vigencia del enlace (30 min)
 * @param linksPerWindow       enlaces por cuenta en {@code linkWindow} (3)
 * @param linkWindow           ventana móvil del límite por cuenta (24 h)
 * @param clientIpHeader       cabecera con la IP del cliente; vacía, también en producción, usa
 *                             {@code getRemoteAddr()} (D10)
 * @param requestsPerIp        pedidos por IP en {@code ipWindow} (10)
 * @param ipWindow             ventana fija del límite por IP (1 h)
 * @param ipCacheMaxSize       IPs contadas a la vez (10 000)
 * @param recoveryQueueCapacity cola del executor de pedidos (100)
 * @param noticeQueueCapacity  cola del executor de avisos (50)
 */
@ConfigurationProperties(prefix = "app.password-recovery")
@Validated
public record PasswordRecoveryProperties(
    Duration linkTtl,
    @Min(1) int linksPerWindow,
    Duration linkWindow,
    String clientIpHeader,
    @Min(1) int requestsPerIp,
    Duration ipWindow,
    @Min(1) int ipCacheMaxSize,
    @Min(1) int recoveryQueueCapacity,
    @Min(1) int noticeQueueCapacity) {

    public PasswordRecoveryProperties {
        requirePositive(linkTtl, "link-ttl");
        requirePositive(linkWindow, "link-window");
        requirePositive(ipWindow, "ip-window");
        clientIpHeader = clientIpHeader == null ? "" : clientIpHeader.trim();
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("app.password-recovery." + name + " debe ser una duración positiva");
        }
    }

    public boolean usesClientIpHeader() {
        return !clientIpHeader.isEmpty();
    }
}
