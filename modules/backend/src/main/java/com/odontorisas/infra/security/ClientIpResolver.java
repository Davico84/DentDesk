package com.odontorisas.infra.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * IP del cliente para el límite de pedidos de recuperación ({@code update-authentication-password-recovery}
 * design.md D10). <b>Una sola fuente:</b> la cabecera configurada ({@code CF-Connecting-IP} en producción), que
 * Cloudflare escribe con la IP que se conectó y pisa si el cliente la manda.
 *
 * <p>Nunca {@code X-Forwarded-For}: el cliente controla sus valores a la izquierda y, con
 * {@code forward-headers-strategy: framework}, {@code getRemoteAddr()} devuelve justamente el primero. Por eso,
 * con la cabecera configurada y ausente, <b>no</b> se cae a {@code getRemoteAddr()}, que sería falsificable:
 * todos esos pedidos comparten un único cupo. Sin cabecera configurada (local, tests) se usa la dirección de la
 * conexión.
 */
@Component
public class ClientIpResolver {

    /** Cupo compartido por los pedidos que llegan sin la cabecera configurada. */
    static final String UNKNOWN = "unknown";

    private final PasswordRecoveryProperties properties;

    public ClientIpResolver(PasswordRecoveryProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        if (!properties.usesClientIpHeader()) {
            return request.getRemoteAddr();
        }
        String value = request.getHeader(properties.clientIpHeader());
        return value == null || value.isBlank() ? UNKNOWN : value.trim();
    }
}
