package com.odontorisas.presentation.dto;

import com.odontorisas.common.DentalArch;
import com.odontorisas.common.LocationKind;
import com.odontorisas.common.ToothSurface;
import com.odontorisas.service.dentalchart.FindingLocationData;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Espejo de {@link DentalChartLocationRequest} para la respuesta — mismos campos, misma forma. */
public record DentalChartLocationResponse(
    @Schema(description = "SURFACE | TOOTH | BETWEEN_TEETH | TOOTH_RANGE | ARCH")
    LocationKind locationKind,

    @Schema(description = "Número FDI. Pieza principal en TOOTH/SURFACE; la menor en BETWEEN_TEETH.")
    Integer toothA,

    @Schema(description = "Número FDI. Solo BETWEEN_TEETH.")
    Integer toothB,

    @Schema(description = "Superficie dental. Solo SURFACE.")
    ToothSurface surface,

    @Schema(description = "Arco. Solo ARCH.")
    DentalArch arch,

    @Schema(description = "Piezas del rango, en el orden persistido (identifica los pilares). Solo TOOTH_RANGE.")
    List<Integer> teeth) {

    public static DentalChartLocationResponse from(FindingLocationData l) {
        return new DentalChartLocationResponse(l.locationKind(), l.toothA(), l.toothB(), l.surface(), l.arch(), l.teeth());
    }
}
