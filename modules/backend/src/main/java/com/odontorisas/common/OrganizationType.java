package com.odontorisas.common;

/**
 * Tipo de organización (tenant). Enum acotado, persistido como {@code varchar} para
 * que añadir un tipo futuro no requiera migración. En este producto el registro crea
 * siempre {@link #CLINIC}.
 */
public enum OrganizationType {
    CLINIC
}
