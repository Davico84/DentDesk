package com.odontorisas.infra.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * IP del cliente para el límite de pedidos de recuperación ({@code update-authentication-password-recovery}
 * design.md D10). <b>En producción no hay cabecera configurada:</b> {@code getRemoteAddr()}, que
 * {@code ForwardedHeaderFilter} toma de la cabecera {@code Forwarded} que escribe Vercel al reenviar {@code /api/*}
 * y que el cliente no pudo cambiar a través de Vercel. Quien llame al backend directo puede inventarla (riesgo
 * aceptado en D10).
 *
 * <p>Con una cabecera configurada se lee solo esa; si falta, <b>no</b> se cae a {@code getRemoteAddr()}: todos esos
 * pedidos comparten un único cupo.
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
