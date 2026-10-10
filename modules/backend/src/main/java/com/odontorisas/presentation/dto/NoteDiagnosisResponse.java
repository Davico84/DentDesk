package com.odontorisas.presentation.dto;

import com.odontorisas.common.DiagnosisLocation;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.service.consultationnote.NoteDiagnosisView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record NoteDiagnosisResponse(
    Long id,
    String cie10Code,
    DiagnosisLocation ubicacion,
    Integer fdiNumber,
    TipoDiagnostico tipoDiagnostico,
    @Schema(description = "Fecha/hora de registro, inmutable.")
    Instant createdAt,
    Long createdBy) {

    public static NoteDiagnosisResponse from(NoteDiagnosisView v) {
        return new NoteDiagnosisResponse(v.id(), v.cie10Code(), v.ubicacion(), v.fdiNumber(),
            v.tipoDiagnostico(), v.createdAt(), v.createdBy());
    }
}
