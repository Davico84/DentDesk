package com.odontorisas.presentation.dto;

import com.odontorisas.common.DiagnosisLocation;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.service.clinicalhistory.DiagnosisView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record DiagnosisResponse(
    Long id,
    String cie10Code,
    DiagnosisLocation ubicacion,
    Integer fdiNumber,
    TipoDiagnostico tipoDiagnostico,
    @Schema(description = "Fecha/hora de registro, inmutable (norma MINSA HIS).")
    Instant createdAt,
    @Schema(description = "Usuario que registró el diagnóstico (colegiatura COP diferida a staff).")
    Long createdBy) {

    public static DiagnosisResponse from(DiagnosisView v) {
        return new DiagnosisResponse(v.id(), v.cie10Code(), v.ubicacion(), v.fdiNumber(),
            v.tipoDiagnostico(), v.createdAt(), v.createdBy());
    }
}
