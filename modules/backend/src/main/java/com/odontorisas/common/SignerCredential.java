package com.odontorisas.common;

import java.time.LocalDate;

/**
 * La credencial copiada en un registro firmado, tal como se lee de vuelta (nombre, COP y "habilitado
 * hasta" al firmar). Vive en {@code common} para que las vistas de servicio y los DTOs la compartan sin
 * que presentación dependa de la entidad embebible de persistencia.
 */
public record SignerCredential(String fullName, String copNumber, LocalDate licensedUntil) {}
