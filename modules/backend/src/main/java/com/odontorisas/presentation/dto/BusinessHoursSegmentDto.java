package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Tramo de apertura y cierre en hora de la clínica, {@code "HH:mm"} en múltiplos de 5 minutos
 * (add-appointments design.md D5). Que abra antes de cerrar y no se solape con otro lo valida el servicio.
 */
public record BusinessHoursSegmentDto(
    @NotNull
    @Pattern(regexp = BusinessHoursSegmentDto.TIME, message = "Hora HH:mm en múltiplos de 5 minutos")
    @Schema(example = "09:00", description = "Apertura, HH:mm en hora de la clínica")
    String opens,

    @NotNull
    @Pattern(regexp = BusinessHoursSegmentDto.TIME, message = "Hora HH:mm en múltiplos de 5 minutos")
    @Schema(example = "13:00", description = "Cierre, HH:mm en hora de la clínica")
    String closes) {

    /** 00:00 a 23:55, minutos terminados en 0 o 5. */
    static final String TIME = "^([01]\\d|2[0-3]):[0-5][05]$";
}
