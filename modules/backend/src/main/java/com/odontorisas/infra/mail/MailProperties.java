package com.odontorisas.infra.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Configuración externalizada del correo transaccional. {@code timeout} acota conexión, lectura y
 * escritura SMTP: sin él, un puerto bloqueado (Render free bloquea 25/465/587) cuelga la petición
 * hasta que el sistema operativo corta la conexión, con la transacción abierta.
 *
 * <p>{@code localSmtp} ({@code MAIL_LOCAL_SMTP}) es el modo de desarrollo contra un servidor de prueba como
 * Mailpit: sin STARTTLS ni autenticación ({@code update-project-foundation-local-mail} design D1). Con el correo
 * habilitado solo se acepta un host de loopback escrito exactamente, y se valida al construir el record, que
 * Spring hace al arrancar: un despliegue que lo encienda contra un SMTP real no llega a levantar (design D2).
 * Protege contra un error de configuración, no contra un túnel o relay en {@code localhost} puesto a propósito.
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(boolean enabled, String host, int port, String username,
                             String password, String from, String frontendUrl, Duration timeout,
                             boolean localSmtp) {

    /** Únicas formas de host aceptadas en modo local (sin distinguir mayúsculas, tras recortar espacios). */
    static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    /** Un host que se puede mostrar: solo nombre, IPv4 o IPv6. Sin {@code @}, espacios ni caracteres de control. */
    private static final Pattern DISPLAYABLE_HOST = Pattern.compile("[A-Za-z0-9.:\\[\\]-]{1,253}");

    public MailProperties {
        if (enabled && localSmtp && !isLocalHost(host)) {
            // Nombra la variable y el host solo si es un nombre limpio: un valor con userinfo
            // (usuario:clave@host) o caracteres de control no se muestra.
            throw new IllegalStateException("MAIL_LOCAL_SMTP=true solo admite un host SMTP local (localhost, "
                + "127.0.0.1 o ::1), y MAIL_HOST es " + displayable(host) + ". Apaga MAIL_LOCAL_SMTP para usar "
                + "un servidor SMTP real con STARTTLS y autenticación.");
        }
    }

    static boolean isLocalHost(String host) {
        return host != null && LOCAL_HOSTS.contains(host.strip().toLowerCase(Locale.ROOT));
    }

    static String displayable(String host) {
        if (host == null || host.isBlank()) {
            return "vacío";
        }
        String trimmed = host.strip();
        return DISPLAYABLE_HOST.matcher(trimmed).matches() ? "'" + trimmed + "'" : "un valor que no se muestra";
    }
}
