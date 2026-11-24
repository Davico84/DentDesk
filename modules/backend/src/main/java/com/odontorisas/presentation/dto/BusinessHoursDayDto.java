package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.util.List;

/** Tramos de un día de la semana; sin tramos, la clínica no atiende ese día. */
public record BusinessHoursDayDto(
    @NotNull
    @Schema(example = "MONDAY")
    DayOfWeek day,

    @NotNull
    @Size(max = 6, message = "Como máximo 6 tramos por día")
    List<@NotNull @Valid BusinessHoursSegmentDto> segments) {
}
