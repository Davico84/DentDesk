package com.odontorisas.common;

/**
 * Estado de conservación de un hallazgo {@code SEGUN_ESTADO} (ADR-0017 D1). Es el <b>único</b>
 * dato de color que se persiste: para {@link ColorBehavior#SIEMPRE_AZUL} y {@link
 * ColorBehavior#SIEMPRE_ROJO} el color no depende de ningún estado, se deriva del tipo.
 *
 * <p>NTS 188 5.13: <i>"azul indica un hallazgo clínico en buen estado y rojo un hallazgo en mal
 * estado, temporal o con características patológicas"</i>.
 */
public enum FindingCondition {
    /** Se pinta azul. */
    GOOD,
    /** Se pinta rojo. */
    POOR
}
