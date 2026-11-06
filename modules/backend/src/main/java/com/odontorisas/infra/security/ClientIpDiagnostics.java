package com.odontorisas.infra.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.StringJoiner;

/**
 * <b>Temporal.</b> Diagnóstico de qué cabeceras de IP llegan al backend en producción, para elegir la fuente del
 * límite por IP de la recuperación ({@code update-authentication-password-recovery} design.md D10): con
 * {@code X-Real-IP}, un pedido desde otra red no se atendió tras llenar el cupo desde la primera.
 *
 * <p>Apagado salvo {@code PASSWORD_RECOVERY_IP_DIAGNOSTIC=true}. No registra IPs: por cada cabecera, si falta o
 * una huella (HMAC-SHA256 truncado) con una clave aleatoria de este arranque, que no se puede revertir y solo
 * sirve para comparar pedidos entre sí. Se retira cuando se corrija la fuente.
 */
@Component
public class ClientIpDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(ClientIpDiagnostics.class);

    /** {@code X-Forwarded-For} no figura: {@code ForwardedHeaderFilter} la oculta; su efecto se ve en remoteAddr. */
    private static final List<String> HEADERS = List.of(
        "X-Real-IP", "CF-Connecting-IP", "True-Client-IP", "X-Vercel-Forwarded-For");

    private final boolean enabled;
    private final byte[] key = new byte[32];

    public ClientIpDiagnostics(@Value("${app.password-recovery.ip-diagnostic:false}") boolean enabled) {
        this.enabled = enabled;
        new SecureRandom().nextBytes(key);
    }

    public void record(HttpServletRequest request, String resolvedIp) {
        if (!enabled) {
            return;
        }
        StringJoiner line = new StringJoiner(" ");
        for (String header : HEADERS) {
            line.add(header + "=" + describe(request.getHeader(header)));
        }
        line.add("remoteAddr=" + describe(request.getRemoteAddr()));
        line.add("resolved=" + describe(resolvedIp));
        log.info("Diagnóstico IP recuperación: {}", line);
    }

    private String describe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        StringJoiner parts = new StringJoiner(",");
        for (String part : value.split(",")) {
            parts.add(fingerprint(part.trim()));
        }
        return parts.toString();
    }

    private String fingerprint(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (GeneralSecurityException e) {
            return "?";
        }
    }
}
