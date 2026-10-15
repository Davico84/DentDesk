package com.odontorisas.infra.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración externalizada del correo transaccional. */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(boolean enabled, String host, int port, String username,
                             String password, String from, String frontendUrl) {}
