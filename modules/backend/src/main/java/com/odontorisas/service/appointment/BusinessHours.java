package com.odontorisas.service.appointment;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Horario de atención de la clínica: para cada día de la semana, cero o más tramos (add-appointments
 * design.md D5). Un día sin tramos está cerrado. Inmutable y siempre válido: solo se construye con
 * {@link #of(Map)}, que rechaza tramos fuera de la grilla de 5 minutos, con apertura no anterior al cierre o
 * solapados.
 *
 * <p>Las horas son <b>civiles</b>, de la zona de la clínica: este tipo no sabe de instantes. Quien lo consulta
 * convierte antes el instante con {@code Instant.atZone(zonaDeLaClínica)} (docs/backend.md §8.2).
 */
public final class BusinessHours {

    /** Lunes a viernes 09:00–18:00, sábado 09:00–13:00, domingo cerrado: el DEFAULT de V32. */
    public static final BusinessHours DEFAULT = defaultWeek();

    private final Map<DayOfWeek, List<Segment>> days;

    private BusinessHours(Map<DayOfWeek, List<Segment>> days) {
        this.days = days;
    }

    /** Un tramo de apertura y cierre del mismo día, en hora de la clínica. */
    public record Segment(LocalTime opens, LocalTime closes) {

        boolean covers(LocalTime start, LocalTime end) {
            return !start.isBefore(opens) && !end.isAfter(closes);
        }
    }

    /**
     * Construye un horario válido. Un día ausente queda cerrado; los tramos se guardan ordenados.
     *
     * @throws InvalidBusinessHoursException si un tramo no está en la grilla de 5 minutos, no abre antes de
     *     cerrar, o se solapa con otro del mismo día
     */
    public static BusinessHours of(Map<DayOfWeek, List<Segment>> requested) {
        Map<DayOfWeek, List<Segment>> days = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            List<Segment> segments = new ArrayList<>(requested.getOrDefault(day, List.of()));
            segments.sort(Comparator.comparing(Segment::opens));
            for (int i = 0; i < segments.size(); i++) {
                Segment segment = segments.get(i);
                if (!onGrid(segment.opens()) || !onGrid(segment.closes())) {
                    throw new InvalidBusinessHoursException(day, "las horas deben ser múltiplos de 5 minutos");
                }
                if (!segment.opens().isBefore(segment.closes())) {
                    throw new InvalidBusinessHoursException(day, "la apertura debe ser anterior al cierre");
                }
                if (i > 0 && segment.opens().isBefore(segments.get(i - 1).closes())) {
                    throw new InvalidBusinessHoursException(day, "los tramos no pueden solaparse");
                }
            }
            days.put(day, List.copyOf(segments));
        }
        return new BusinessHours(days);
    }

    private static boolean onGrid(LocalTime time) {
        return time.getSecond() == 0 && time.getNano() == 0 && time.getMinute() % 5 == 0;
    }

    public List<Segment> segments(DayOfWeek day) {
        return days.get(day);
    }

    public boolean isClosed(DayOfWeek day) {
        return days.get(day).isEmpty();
    }

    /**
     * Si el intervalo civil cae completo dentro de un mismo tramo del día de su inicio. Recibe hora civil
     * <b>derivada de un instante</b> en la zona de la clínica, nunca un valor persistido ni de la API.
     */
    public boolean contains(LocalDateTime civilStart, LocalDateTime civilEnd) {
        if (!civilEnd.toLocalDate().equals(civilStart.toLocalDate())) {
            return false;
        }
        LocalTime start = civilStart.toLocalTime();
        LocalTime end = civilEnd.toLocalTime();
        return days.get(civilStart.getDayOfWeek()).stream().anyMatch(s -> s.covers(start, end));
    }

    /** Copia mutable para serializar; el orden de días es lunes a domingo. */
    public Map<DayOfWeek, List<Segment>> asMap() {
        return new EnumMap<>(days);
    }

    private static BusinessHours defaultWeek() {
        Map<DayOfWeek, List<Segment>> week = new EnumMap<>(DayOfWeek.class);
        Segment fullDay = new Segment(LocalTime.of(9, 0), LocalTime.of(18, 0));
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY)) {
            week.put(day, List.of(fullDay));
        }
        week.put(DayOfWeek.SATURDAY, List.of(new Segment(LocalTime.of(9, 0), LocalTime.of(13, 0))));
        return of(week);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BusinessHours that && days.equals(that.days);
    }

    @Override
    public int hashCode() {
        return days.hashCode();
    }

    @Override
    public String toString() {
        return "BusinessHours" + days;
    }
}
