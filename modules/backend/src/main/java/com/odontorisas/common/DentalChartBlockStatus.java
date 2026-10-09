package com.odontorisas.common;

/**
 * Estado del bloque del odontograma (design.md D1/D9, {@code add-dental-chart}). Transición
 * unidireccional {@code OPEN → SEALED}: un bloque sellado no vuelve a abrirse ni se vuelve a
 * sellar (ADR-0012 D3, el odontograma es un libro de asientos, no un estado mutable).
 */
public enum DentalChartBlockStatus {
    OPEN,
    SEALED
}
