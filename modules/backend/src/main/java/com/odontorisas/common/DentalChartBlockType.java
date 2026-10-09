package com.odontorisas.common;

/**
 * Tipo de bloque del odontograma. Este change acota el alcance al odontograma <b>inicial</b>
 * (ADR-0012 D9, {@code add-dental-chart}): un único valor por ahora. La evolución (odontogramas
 * posteriores por evento clínico, NTS 188 5.10/5.11) llega en {@code update-dental-chart}, que
 * amplía este enum — no lo reemplaza.
 */
public enum DentalChartBlockType {
    INITIAL
}
