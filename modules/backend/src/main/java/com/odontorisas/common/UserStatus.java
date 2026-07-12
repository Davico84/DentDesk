package com.odontorisas.common;

/**
 * Ciclo de vida de la identidad (a nivel de plataforma, independiente de sus
 * membresías). Solo se autentica una identidad {@link #ACTIVE}.
 */
public enum UserStatus {
    ACTIVE,
    LOCKED,
    DISABLED
}
