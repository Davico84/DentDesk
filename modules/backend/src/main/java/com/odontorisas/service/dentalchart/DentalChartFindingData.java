package com.odontorisas.service.dentalchart;

import com.odontorisas.common.FindingCondition;
import com.odontorisas.common.FindingType;

/**
 * Un hallazgo del odontograma, en el lenguaje de la <b>capa de servicio</b> — mismo patrón que
 * {@code DiagnosisData}. El par {@code (type, subtype)}, la coherencia de {@code location}, el
 * {@code grade} y la {@code condition} ya llegaron validados por {@code @ValidFindingLocation} en
 * el DTO (design.md D9); este servicio solo valida lo que necesita el estado ya persistido
 * (bloque abierto, duplicados, pieza ausente).
 *
 * <p>{@code subtype} es {@code ""} cuando {@link #type} no tiene subtipos — nunca {@code null},
 * para que coincida con la clave del catálogo (V16) y con la columna de {@code
 * dental_chart_finding}.
 */
public record DentalChartFindingData(
    FindingType type,
    String subtype,
    FindingCondition condition,
    Short grade,
    FindingLocationData location) {
}
