package com.odontorisas.infra.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Credenciales temporales para el bootstrap operativo, desactivado por defecto. */
@ConfigurationProperties(prefix = "app.platform.bootstrap")
public record PlatformBootstrapProperties(boolean enabled, String email, String fullName,
                                          String password) {}
