package com.odontorisas.infra.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuración externalizada del correo transaccional. {@code timeout} acota conexión, lectura y
 * escritura SMTP: sin él, un puerto bloqueado (Render free bloquea 25/465/587) cuelga la petición
 * hasta que el sistema operativo corta la conexión, con la transacción abierta.
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(boolean enabled, String host, int port, String username,
                             String password, String from, String frontendUrl, Duration timeout) {}
