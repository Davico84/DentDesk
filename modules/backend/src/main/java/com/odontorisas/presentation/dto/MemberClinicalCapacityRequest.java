package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Retirar ({@code true}) o restituir ({@code false}) la capacidad clínica de un miembro (design.md D2). */
public record MemberClinicalCapacityRequest(
    @Schema(description = "true retira la capacidad clínica en esta clínica; false levanta el retiro.")
    @NotNull Boolean revoked) {}
