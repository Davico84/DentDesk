package com.odontorisas.common;

/**
 * Los tres comportamientos de color que declara cada {@link FindingType} (ADR-0017 D1). Califican
 * al <b>hallazgo</b>, no a la pieza ni al tipo por sí solo — no existe una regla de "contagio" que
 * pinte un hallazgo según los demás de la misma pieza (giroversión 6.1.13 se pinta azul sin
 * condicional; ADR-0017 D2 elimina esa regla de {@code ADR-0012} D2, que no está en la norma).
 */
public enum ColorBehavior {
    /** Siempre azul, sin excepción (p. ej. giroversión, macrodoncia). */
    SIEMPRE_AZUL,
    /** Siempre rojo, sin excepción — incluye lo <b>temporal</b>: no es un eje aparte, es un tipo
     *  distinto que declara este comportamiento (corona temporal {@code CT} ≠ corona {@code CM}). */
    SIEMPRE_ROJO,
    /** El color lo decide el campo {@link FindingCondition} de la propia fila del hallazgo. */
    SEGUN_ESTADO
}
