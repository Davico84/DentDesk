package com.odontorisas.presentation.dto;

import com.odontorisas.common.FindingCondition;
import com.odontorisas.common.FindingType;
import com.odontorisas.service.dentalchart.DentalChartFindingView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record DentalChartFindingResponse(
    Long id,
    Long blockId,

    @Schema(description = "Uno de los 38 tipos del catálogo NTS 188 (6.1.1-6.1.38).")
    FindingType type,

    @Schema(description = "Subtipo del tipo elegido. \"\" si el tipo no tiene subtipos.")
    String subtype,

    @Schema(description = "GOOD | POOR. Presente solo si el tipo es SEGUN_ESTADO.", nullable = true)
    FindingCondition condition,

    @Schema(description = "Grado de movilidad (1-3). Presente solo en MOVILIDAD_PATOLOGICA.", nullable = true)
    Short grade,

    DentalChartLocationResponse location,

    @Schema(description = "Fecha/hora de registro, inmutable mientras el bloque siga OPEN.")
    Instant createdAt,

    @Schema(description = "Usuario que registró el hallazgo.")
    Long createdBy) {

    public static DentalChartFindingResponse from(DentalChartFindingView v) {
        return new DentalChartFindingResponse(
            v.id(), v.blockId(), FindingType.valueOf(v.type()), v.subtype(), v.condition(), v.grade(),
            DentalChartLocationResponse.from(v.location()), v.createdAt(), v.createdBy());
    }
}
