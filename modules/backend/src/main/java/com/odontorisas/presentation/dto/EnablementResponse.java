package com.odontorisas.presentation.dto;

import com.odontorisas.service.clinicalhistory.EnablementView;
import io.swagger.v3.oas.annotations.media.Schema;

/** Estado de habilitación de un paciente para tratamientos (design.md D8). */
public record EnablementResponse(
    @Schema(description = "NONE | DRAFT | COMPLETED")
    String status,
    @Schema(description = "true solo si status=COMPLETED — la señal que consume treatment-plans.")
    boolean enabledForTreatment) {

    public static EnablementResponse from(EnablementView v) {
        return new EnablementResponse(v.status(), v.enabledForTreatment());
    }
}
