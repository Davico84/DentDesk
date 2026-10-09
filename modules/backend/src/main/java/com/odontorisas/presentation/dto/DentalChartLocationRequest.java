package com.odontorisas.presentation.dto;

import com.odontorisas.common.DentalArch;
import com.odontorisas.common.LocationKind;
import com.odontorisas.common.ToothSurface;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Localización de un hallazgo del odontograma: cinco formas (ADR-0017 D4). Solo las columnas de
 * la variante elegida van pobladas; el resto queda {@code null} — la coherencia la exige {@code
 * @ValidFindingLocation} sobre el {@link DentalChartFindingRequest} que la contiene, no esta
 * clase por sí sola (depende también de {@code type}, así que no puede validarse aislada).
 */
public record DentalChartLocationRequest(
    @Schema(description = "SURFACE | TOOTH | BETWEEN_TEETH | TOOTH_RANGE | ARCH")
    LocationKind locationKind,

    @Schema(description = "Número FDI. Pieza principal en TOOTH/SURFACE; la menor en BETWEEN_TEETH.", example = "36")
    Integer toothA,

    @Schema(description = "Número FDI. Solo BETWEEN_TEETH.", example = "37")
    Integer toothB,

    @Schema(description = "Superficie dental. Solo SURFACE.")
    ToothSurface surface,

    @Schema(description = "Arco. Solo ARCH.")
    DentalArch arch,

    @Schema(description = "Piezas del rango, en orden (identifica los pilares). Solo TOOTH_RANGE, mínimo dos.")
    List<Integer> teeth) {
}
