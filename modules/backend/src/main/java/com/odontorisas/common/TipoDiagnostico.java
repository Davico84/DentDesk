package com.odontorisas.common;

/**
 * Tipo de diagnóstico según la norma MINSA HIS (design.md D11, `add-clinical-history`):
 * {@code PRESUNTIVO} (P), {@code DEFINITIVO} (D), {@code REPETIDO} (R). Obligatorio en cada
 * diagnóstico — un diagnóstico no es una cadena suelta.
 */
public enum TipoDiagnostico {
    PRESUNTIVO,
    DEFINITIVO,
    REPETIDO
}
