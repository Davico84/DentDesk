package com.odontorisas.common;

/**
 * Ciclo de vida de una organización. Solo se autentica sobre organizaciones
 * {@link #ACTIVE} (ver gate por estado en la autenticación).
 */
public enum OrganizationStatus {
    ACTIVE,
    SUSPENDED
}
