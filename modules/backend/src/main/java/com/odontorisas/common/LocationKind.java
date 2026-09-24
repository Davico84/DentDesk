package com.odontorisas.common;

/**
 * Las cinco formas de localizar un hallazgo del odontograma (ADR-0017 D4, {@code add-dental-chart}
 * design.md D2) — no las tres de {@code ADR-0012} D8, que la norma desmintió.
 *
 * <ul>
 *   <li>{@link #SURFACE} — una superficie de una pieza (caries, restauración, sellante).</li>
 *   <li>{@link #TOOTH} — una pieza completa (corona, movilidad, pieza ausente).</li>
 *   <li>{@link #BETWEEN_TEETH} — entre dos piezas, sin número FDI propio (diastema, fusión,
 *       supernumeraria).</li>
 *   <li>{@link #TOOTH_RANGE} — un conjunto ordenado de piezas de cardinalidad variable (prótesis
 *       parcial fija, ortodoncia fija) — <b>un</b> hallazgo, no uno por pieza.</li>
 *   <li>{@link #ARCH} — el arco completo, superior o inferior (edéntulo total, prótesis
 *       completa).</li>
 * </ul>
 */
public enum LocationKind {
    SURFACE,
    TOOTH,
    BETWEEN_TEETH,
    TOOTH_RANGE,
    ARCH
}
