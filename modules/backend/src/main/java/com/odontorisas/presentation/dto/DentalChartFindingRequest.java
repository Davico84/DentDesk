package com.odontorisas.presentation.dto;

import com.odontorisas.common.FindingCondition;
import com.odontorisas.common.FindingType;
import com.odontorisas.presentation.dto.validation.ValidFindingLocation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Un hallazgo del odontograma (ADR-0017 D5/D7). {@code type}, {@code subtype}, {@code condition},
 * {@code grade} y {@code location} van emparejados en el sentido correcto —{@link
 * ValidFindingLocation} cubre las seis invariantes de coherencia a la vez (design.md D9): alcance
 * del tipo ↔ forma de la localización, subtipo exigido/prohibido y perteneciente al tipo, grado
 * exigido solo por movilidad, estado de conservación exigido solo en {@code SEGUN_ESTADO}, FDI
 * real y superficie aplicable a la pieza, y rango con más de una pieza.
 */
@ValidFindingLocation
public record DentalChartFindingRequest(
    @NotNull
    @Schema(description = "Uno de los 38 tipos del catálogo NTS 188 (6.1.1-6.1.38).")
    FindingType type,

    @Schema(description = "Subtipo del tipo elegido. \"\" o ausente si el tipo no tiene subtipos.")
    String subtype,

    @Schema(description = "GOOD | POOR. Exigido solo si el tipo es SEGUN_ESTADO; prohibido en los demás.")
    FindingCondition condition,

    @Schema(description = "Grado de movilidad (1-3). Exigido solo por MOVILIDAD_PATOLOGICA; prohibido en los demás.")
    Short grade,

    @NotNull
    @Valid
    DentalChartLocationRequest location) {
}
