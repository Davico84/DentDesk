package com.odontorisas.presentation.dto;

import com.odontorisas.service.appointment.BusinessHoursService.ClinicSchedule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Horario de atención de la organización activa y su zona horaria (add-appointments design.md D3): el cliente
 * muestra y elige las horas de la agenda en esta zona, nunca en la del dispositivo.
 */
public record BusinessHoursResponse(
    @Schema(example = "America/Lima", description = "Zona horaria IANA de la clínica")
    String timezone,

    @Schema(description = "Los siete días, de lunes a domingo")
    List<BusinessHoursDayDto> days) {

    public static BusinessHoursResponse from(ClinicSchedule schedule) {
        List<BusinessHoursDayDto> days = schedule.hours().asMap().entrySet().stream()
            .map(e -> new BusinessHoursDayDto(e.getKey(), e.getValue().stream()
                .map(s -> new BusinessHoursSegmentDto(s.opens().toString(), s.closes().toString()))
                .toList()))
            .toList();
        return new BusinessHoursResponse(schedule.zone().getId(), days);
    }
}
