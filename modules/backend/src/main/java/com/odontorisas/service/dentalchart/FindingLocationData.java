package com.odontorisas.service.dentalchart;

import com.odontorisas.common.DentalArch;
import com.odontorisas.common.LocationKind;
import com.odontorisas.common.ToothSurface;

import java.util.List;

/**
 * La localización de un hallazgo, en el lenguaje de la <b>capa de servicio</b> — mismo patrón que
 * {@code DiagnosisData} respecto de {@code DiagnosisRequest}: ya llega validada por {@code
 * @ValidFindingLocation} en el DTO (design.md D9), así que acá no se repite esa validación.
 *
 * <p>{@code teeth} solo se usa para {@link LocationKind#TOOTH_RANGE} — el orden de la lista es la
 * {@code position} que se persiste en {@code dental_chart_finding_tooth} (ADR-0017 D4: el orden
 * de un rango SÍ es dato, no se normaliza).
 */
public record FindingLocationData(
    LocationKind locationKind,
    Integer toothA,
    Integer toothB,
    ToothSurface surface,
    DentalArch arch,
    List<Integer> teeth) {
}
