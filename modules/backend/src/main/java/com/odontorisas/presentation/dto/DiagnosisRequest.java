package com.odontorisas.presentation.dto;

import com.odontorisas.common.DiagnosisLocation;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.presentation.dto.validation.ValidDiagnosisLocation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Un diagnóstico agregado a la Historia Clínica (design.md D5/D11, ADR-0011, norma MINSA HIS).
 * {@code ubicacion} y {@code fdiNumber} van emparejados en el sentido correcto: {@code PIEZA}
 * exige el número, {@code GENERAL} lo rechaza ({@link ValidDiagnosisLocation}). El código CIE10
 * se valida contra el catálogo en el servicio (necesita la base de datos, no es Bean Validation).
 */
@ValidDiagnosisLocation
public record DiagnosisRequest(
    @NotBlank
    @Schema(example = "A00", description = "Código CIE10, existente en el catálogo oficial MINSA.")
    String cie10Code,

    @NotNull
    @Schema(description = "GENERAL | PIEZA")
    DiagnosisLocation ubicacion,

    @Schema(description = "Número FDI. Obligatorio si ubicacion=PIEZA; ausente si GENERAL.", example = "36")
    Integer fdiNumber,

    @NotNull
    @Schema(description = "PRESUNTIVO | DEFINITIVO | REPETIDO — norma MINSA HIS.")
    TipoDiagnostico tipoDiagnostico) {
}
