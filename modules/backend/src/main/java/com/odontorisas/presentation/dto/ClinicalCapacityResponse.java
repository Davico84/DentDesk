package com.odontorisas.presentation.dto;

import com.odontorisas.common.ClinicalCapacityState;
import com.odontorisas.service.staff.ClinicalCapacityService;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** Capacidad clínica de una persona en la clínica activa (design.md D3/D6). */
public record ClinicalCapacityResponse(
    @Schema(description = "ENABLED si puede firmar; si no, el motivo.")
    ClinicalCapacityState state,
    @Schema(description = "Número COP declarado, o null si no declaró credencial.", nullable = true)
    String copNumber,
    @Schema(description = "Fecha \"habilitado hasta\" declarada, o null.", nullable = true)
    LocalDate licensedUntil,
    @Schema(description = "Fecha en que venció la habilitación; solo con state=EXPIRED.", nullable = true)
    LocalDate expiredOn) {

    public static ClinicalCapacityResponse from(ClinicalCapacityService.CapacityView view) {
        return new ClinicalCapacityResponse(view.state(), view.copNumber(), view.licensedUntil(),
            view.state() == ClinicalCapacityState.EXPIRED ? view.licensedUntil() : null);
    }
}
