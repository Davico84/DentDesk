package com.odontorisas.presentation.dto;

import com.odontorisas.service.appointment.BusinessHours;
import com.odontorisas.service.appointment.InvalidBusinessHoursException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Reemplazo de la semana completa del horario de atención: los siete días, una vez cada uno. */
public record UpdateBusinessHoursRequest(
    @NotNull
    @Size(min = 7, max = 7, message = "Deben venir los siete días de la semana")
    List<@NotNull @Valid BusinessHoursDayDto> days) {

    /** Traduce al tipo del servicio; un día repetido es un horario inválido. */
    public BusinessHours toBusinessHours() {
        Map<DayOfWeek, List<BusinessHours.Segment>> week = new EnumMap<>(DayOfWeek.class);
        for (BusinessHoursDayDto day : days) {
            List<BusinessHours.Segment> segments = day.segments().stream()
                .map(s -> new BusinessHours.Segment(LocalTime.parse(s.opens()), LocalTime.parse(s.closes())))
                .toList();
            if (week.put(day.day(), segments) != null) {
                throw new InvalidBusinessHoursException(day.day(), "el día viene repetido");
            }
        }
        return BusinessHours.of(week);
    }
}
