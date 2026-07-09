package com.odontorisas.common;

/**
 * Ciclo de vida de la pertenencia de una identidad a una organización, independiente
 * del estado global de la identidad. Solo se autentica sobre membresías {@link #ACTIVE}.
 */
public enum MembershipStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}
