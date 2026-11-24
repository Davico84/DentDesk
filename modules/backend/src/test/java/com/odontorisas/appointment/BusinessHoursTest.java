package com.odontorisas.appointment;

import com.odontorisas.service.appointment.BusinessHours;
import com.odontorisas.service.appointment.BusinessHours.Segment;
import com.odontorisas.service.appointment.InvalidBusinessHoursException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas del horario de atención (add-appointments, requirements de horario). */
class BusinessHoursTest {

    /** Lunes 2030-01-07. */
    private static final LocalDateTime MONDAY = LocalDateTime.of(2030, 1, 7, 0, 0);

    private static Segment segment(String opens, String closes) {
        return new Segment(LocalTime.parse(opens), LocalTime.parse(closes));
    }

    private static BusinessHours splitShiftMonday() {
        return BusinessHours.of(Map.of(DayOfWeek.MONDAY, List.of(segment("15:00", "19:00"), segment("09:00", "13:00"))));
    }

    private static LocalDateTime at(String time) {
        return MONDAY.with(LocalTime.parse(time));
    }

    @Test
    void a_split_shift_is_valid_and_kept_in_order() {
        assertThat(splitShiftMonday().segments(DayOfWeek.MONDAY))
            .containsExactly(segment("09:00", "13:00"), segment("15:00", "19:00"));
    }

    @Test
    void opening_after_closing_is_rejected() {
        assertThatThrownBy(() -> BusinessHours.of(Map.of(DayOfWeek.MONDAY, List.of(segment("14:00", "10:00")))))
            .isInstanceOf(InvalidBusinessHoursException.class)
            .hasMessageContaining("lunes");
    }

    @Test
    void opening_equal_to_closing_is_rejected() {
        assertThatThrownBy(() -> BusinessHours.of(Map.of(DayOfWeek.MONDAY, List.of(segment("10:00", "10:00")))))
            .isInstanceOf(InvalidBusinessHoursException.class);
    }

    @Test
    void overlapping_segments_are_rejected() {
        assertThatThrownBy(() -> BusinessHours.of(Map.of(DayOfWeek.MONDAY,
                List.of(segment("09:00", "13:00"), segment("12:00", "15:00")))))
            .isInstanceOf(InvalidBusinessHoursException.class);
    }

    @Test
    void a_time_off_the_five_minute_grid_is_rejected() {
        assertThatThrownBy(() -> BusinessHours.of(Map.of(DayOfWeek.MONDAY, List.of(segment("09:07", "12:00")))))
            .isInstanceOf(InvalidBusinessHoursException.class);
    }

    @Test
    void contiguous_segments_are_allowed() {
        BusinessHours hours = BusinessHours.of(Map.of(DayOfWeek.MONDAY,
            List.of(segment("09:00", "13:00"), segment("13:00", "15:00"))));

        assertThat(hours.segments(DayOfWeek.MONDAY)).hasSize(2);
    }

    @Test
    void an_appointment_inside_a_segment_fits() {
        assertThat(splitShiftMonday().contains(at("09:00"), at("09:30"))).isTrue();
    }

    @Test
    void an_appointment_ending_exactly_at_closing_fits() {
        assertThat(splitShiftMonday().contains(at("12:30"), at("13:00"))).isTrue();
    }

    @Test
    void an_appointment_exceeding_closing_does_not_fit() {
        assertThat(splitShiftMonday().contains(at("12:45"), at("13:15"))).isFalse();
    }

    @Test
    void an_appointment_crossing_the_split_shift_does_not_fit() {
        assertThat(splitShiftMonday().contains(at("12:30"), at("15:30"))).isFalse();
    }

    @Test
    void a_closed_day_admits_nothing() {
        BusinessHours hours = splitShiftMonday();
        LocalDateTime sunday = MONDAY.minusDays(1).with(LocalTime.of(10, 0));

        assertThat(hours.isClosed(DayOfWeek.SUNDAY)).isTrue();
        assertThat(hours.contains(sunday, sunday.plusMinutes(30))).isFalse();
    }

    @Test
    void an_appointment_crossing_midnight_does_not_fit() {
        BusinessHours hours = BusinessHours.of(Map.of(DayOfWeek.MONDAY, List.of(segment("20:00", "23:55"))));

        assertThat(hours.contains(at("23:30"), MONDAY.plusDays(1).with(LocalTime.of(0, 30)))).isFalse();
    }

    @Test
    void the_default_week_matches_the_migration() {
        BusinessHours hours = BusinessHours.DEFAULT;

        assertThat(hours.segments(DayOfWeek.FRIDAY)).containsExactly(segment("09:00", "18:00"));
        assertThat(hours.segments(DayOfWeek.SATURDAY)).containsExactly(segment("09:00", "13:00"));
        assertThat(hours.isClosed(DayOfWeek.SUNDAY)).isTrue();
    }
}
