package com.odontorisas.infra.security;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
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
 * una huella por valor (HMAC-SHA256 truncado; una lista separada por comas da una huella por elemento, en orden)
 * con una clave aleatoria de este arranque, que no se puede revertir y solo sirve para comparar pedidos entre sí.
 * Se retira cuando se corrija la fuente.
 */
@Component
public class ClientIpDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(ClientIpDiagnostics.class);

    /** Cabeceras que {@code ForwardedHeaderFilter} no toca: se leen de la petición tal como llega al controller. */
    private static final List<String> HEADERS = List.of(
        "X-Real-IP", "CF-Connecting-IP", "CF-Connecting-IPv6", "True-Client-IP", "X-Vercel-Forwarded-For");

    /** Bytes de la huella: 8 hacen improbable que dos clientes distintos parezcan el mismo. */
    private static final int FINGERPRINT_BYTES = 8;

    private final boolean enabled;
    /** Solo existe encendido: apagado, el componente no hace nada, ni al arrancar. */
    private final byte[] key;

    public ClientIpDiagnostics(@Value("${app.password-recovery.ip-diagnostic:false}") boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            key = new byte[32];
            new SecureRandom().nextBytes(key);
        } else {
            key = null;
        }
    }

    public void record(HttpServletRequest request, String resolvedIp) {
        if (!enabled) {
            return;
        }
        StringJoiner line = new StringJoiner(" ");
        for (String header : HEADERS) {
            line.add(header + "=" + describe(request.getHeader(header)));
        }
        // ForwardedHeaderFilter oculta X-Forwarded-For y Forwarded y cambia remoteAddr por el primer valor de XFF:
        // la petición original, bajo los wrappers, conserva las cabeceras crudas y la conexión real.
        ServletRequest original = unwrap(request);
        if (original instanceof HttpServletRequest raw) {
            line.add("X-Forwarded-For(raw)=" + describe(raw.getHeader("X-Forwarded-For")));
            line.add("Forwarded(raw)=" + (raw.getHeader("Forwarded") == null ? "-" : "presente"));
        }
        line.add("remoteAddr(filtro)=" + describe(request.getRemoteAddr()));
        line.add("remoteAddr(conexión)=" + describe(original.getRemoteAddr()));
        line.add("resolved=" + describe(resolvedIp));
        log.info("Diagnóstico IP recuperación: {}", line);
    }

    private static ServletRequest unwrap(ServletRequest request) {
        ServletRequest current = request;
        while (current instanceof ServletRequestWrapper wrapper) {
            current = wrapper.getRequest();
        }
        return current;
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
            return HexFormat.of().formatHex(digest, 0, FINGERPRINT_BYTES);
        } catch (GeneralSecurityException e) {
            return "?";
        }
    }
}
