package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

public record DentalChartCreateRequest(
    @NotNull
    @PastOrPresent
    @Schema(description = "Fecha del evento clínico (NTS 188 5.10/5.11), no la de creación de la fila.")
    LocalDate examinedOn) {
}
