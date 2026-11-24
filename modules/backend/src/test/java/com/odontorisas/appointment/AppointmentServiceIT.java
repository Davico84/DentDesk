package com.odontorisas.appointment;

import com.odontorisas.common.AppointmentStatus;
import com.odontorisas.common.ClinicalCapacityState;
import com.odontorisas.common.ConsultationNoteStatus;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.consultationnote.AbstractConsultationNoteIT;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.AppointmentRepository;
import com.odontorisas.persistence.repository.ConsultationNoteRepository;
import com.odontorisas.service.appointment.AppointmentClosedException;
import com.odontorisas.service.appointment.AppointmentConflictException;
import com.odontorisas.service.appointment.AppointmentInPastException;
import com.odontorisas.service.appointment.AppointmentNoteAlreadyLinkedException;
import com.odontorisas.service.appointment.AppointmentNotFoundException;
import com.odontorisas.service.appointment.AppointmentNotStartedException;
import com.odontorisas.service.appointment.AppointmentService;
import com.odontorisas.service.appointment.AppointmentService.RescheduleData;
import com.odontorisas.service.appointment.AppointmentService.ScheduleData;
import com.odontorisas.service.appointment.AppointmentView;
import com.odontorisas.service.appointment.BusinessHours;
import com.odontorisas.service.appointment.BusinessHoursService;
import com.odontorisas.service.appointment.DentistNotSchedulableException;
import com.odontorisas.service.appointment.InvalidAppointmentScheduleException;
import com.odontorisas.service.appointment.OutsideBusinessHoursException;
import com.odontorisas.service.consultationnote.ClinicalHistoryNotCompletedException;
import com.odontorisas.service.consultationnote.ConsultationNoteAlreadyOpenException;
import com.odontorisas.service.consultationnote.PatientInactiveException;
import com.odontorisas.service.patient.PatientNotFoundException;
import com.odontorisas.service.staff.ClinicalCapacityRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scenarios de la agenda por el servicio, contra PostgreSQL real con RLS (add-appointments tareas 3.3–3.6):
 * agendar, odontólogo con capacidad, sin choques, horario, reprogramar, ciclo de estados y "atender con nota".
 */
@SpringBootTest
class AppointmentServiceIT extends AbstractConsultationNoteIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    AppointmentService appointmentService;
    @Autowired
    AppointmentRepository appointmentRepository;
    @Autowired
    ConsultationNoteRepository noteRepository;
    @Autowired
    BusinessHoursService businessHoursService;

    /** Habilitado: {@code someUser} (también quien llama). Otro habilitado: {@code otherUser}. */
    private long dentist;
    private long secondDentist;
    private long patient;
    /** ADMIN de la clínica: el único que retira capacidad clínica (trigger de V30). */
    private long admin;
    /** Martes de dentro de dos semanas: futuro, día hábil del horario por defecto. */
    private LocalDate tuesday;

    @BeforeEach
    void seedSchedule() throws SQLException {
        dentist = someUser;
        secondDentist = otherUser;
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("INSERT INTO organization_settings (organization_id) VALUES (" + org + ")");
            try (var rs = s.executeQuery("INSERT INTO users (email, password_hash, full_name) VALUES ('adm-"
                    + UUID.randomUUID() + "@test.local', 'x', 'Admin') RETURNING id")) {
                rs.next();
                admin = rs.getLong(1);
            }
            s.execute("INSERT INTO memberships (user_id, organization_id, role, status) VALUES (" + admin + ", " + org
                + ", 'ADMIN', 'ACTIVE')");
        }
        patient = aPatientWithSealedHistory();
        tuesday = LocalDate.now(LIMA).with(TemporalAdjusters.next(DayOfWeek.TUESDAY)).plusWeeks(1);
    }

    private Instant at(LocalDate date, String time) {
        return date.atTime(LocalTime.parse(time)).atZone(LIMA).toInstant();
    }

    private AppointmentView book(long dentistId, String time, int minutes) {
        return appointmentService.schedule(new ScheduleData(patient, dentistId, at(tuesday, time), minutes, null));
    }

    private void sql(String statement) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute(statement);
        }
    }

    private void revokeAsAdmin(long member) throws SQLException {
        sql("SELECT set_config('app.user_id', '" + admin + "', false); UPDATE memberships SET "
            + "clinical_capacity_revoked_at = now(), clinical_capacity_revoked_by = " + admin + " WHERE user_id = "
            + member + " AND organization_id = " + org);
    }

    /** Usuario miembro de la clínica, con o sin credencial. */
    private long member(boolean withCredential) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            long id;
            try (var rs = s.executeQuery("INSERT INTO users (email, password_hash, full_name) VALUES ('ap-"
                    + UUID.randomUUID() + "@test.local', 'x', 'Dr. Nuevo') RETURNING id")) {
                rs.next();
                id = rs.getLong(1);
            }
            s.execute("INSERT INTO memberships (user_id, organization_id, role, status) VALUES (" + id + ", " + org
                + ", 'MEMBER', 'ACTIVE')");
            if (withCredential) {
                com.odontorisas.ClinicalSignerFixtures.enableSigner(owner, id, org);
            }
            return id;
        }
    }

    /** Una cita que empezó hace 10 minutos (no se puede agendar en el pasado: se siembra). */
    private long startedAppointment(long patientId) throws SQLException {
        Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             var rs = s.executeQuery("INSERT INTO appointments (organization_id, patient_id, dentist_user_id, starts_at, "
                 + "ends_at, created_by, updated_by) VALUES (" + org + ", " + patientId + ", " + dentist + ", '" + start
                 + "', '" + start.plus(30, ChronoUnit.MINUTES) + "', " + someUser + ", " + someUser + ") RETURNING id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    // --- Agendar una cita ---

    @Test
    void a_valid_appointment_is_scheduled_and_listed_in_its_week() {
        AppointmentView created = book(dentist, "10:00", 30);

        assertThat(created.status()).isEqualTo(AppointmentStatus.AGENDADA);
        assertThat(created.endsAt()).isEqualTo(at(tuesday, "10:30"));
        assertThat(created.durationMinutes()).isEqualTo(30);
        LocalDate monday = tuesday.minusDays(1);
        assertThat(appointmentService.list(monday, monday.plusDays(6), null))
            .extracting(AppointmentView::id).containsExactly(created.id());
    }

    @Test
    void an_archived_patient_cannot_be_scheduled() {
        Patient archived = patientRepository.findById(patient).orElseThrow();
        archived.setStatus(PatientStatus.INACTIVE);
        patientRepository.save(archived);

        assertThatThrownBy(() -> book(dentist, "10:00", 30)).isInstanceOf(PatientInactiveException.class);
    }

    @Test
    void a_missing_patient_is_not_found() {
        assertThatThrownBy(() -> appointmentService.schedule(
                new ScheduleData(999_999L, dentist, at(tuesday, "10:00"), 30, null)))
            .isInstanceOf(PatientNotFoundException.class);
    }

    @Test
    void a_start_in_the_past_is_rejected() {
        LocalDate lastTuesday = LocalDate.now(LIMA).with(TemporalAdjusters.previous(DayOfWeek.TUESDAY));

        assertThatThrownBy(() -> appointmentService.schedule(
                new ScheduleData(patient, dentist, at(lastTuesday, "10:00"), 30, null)))
            .isInstanceOf(AppointmentInPastException.class);
    }

    @Test
    void a_duration_off_the_grid_or_out_of_range_is_rejected_with_its_field() {
        for (int minutes : new int[] {7, 0, 300}) {
            assertThatThrownBy(() -> book(dentist, "10:00", minutes))
                .isInstanceOf(InvalidAppointmentScheduleException.class)
                .extracting("field").isEqualTo("durationMinutes");
        }
    }

    @Test
    void a_start_off_the_five_minute_grid_is_rejected_with_its_field() {
        assertThatThrownBy(() -> book(dentist, "10:03", 30))
            .isInstanceOf(InvalidAppointmentScheduleException.class)
            .extracting("field").isEqualTo("startsAt");
    }

    // --- Odontólogo con capacidad clínica ---

    @Test
    void a_dentist_without_a_credential_is_not_schedulable() throws SQLException {
        long noCredential = member(false);

        assertThatThrownBy(() -> book(noCredential, "10:00", 30))
            .isInstanceOf(DentistNotSchedulableException.class)
            .extracting("reason").isEqualTo(ClinicalCapacityState.NO_CREDENTIAL);
    }

    @Test
    void a_dentist_with_revoked_capacity_is_not_schedulable() throws SQLException {
        revokeAsAdmin(dentist);

        assertThatThrownBy(() -> book(dentist, "10:00", 30))
            .isInstanceOf(DentistNotSchedulableException.class)
            .extracting("reason").isEqualTo(ClinicalCapacityState.REVOKED);
    }

    @Test
    void a_dentist_with_an_inactive_membership_is_not_schedulable() throws SQLException {
        sql("UPDATE memberships SET status = 'INACTIVE' WHERE user_id = " + secondDentist + " AND organization_id = " + org);

        assertThatThrownBy(() -> book(secondDentist, "10:00", 30))
            .isInstanceOf(DentistNotSchedulableException.class)
            .extracting("reason").isEqualTo(ClinicalCapacityState.INACTIVE_MEMBERSHIP);
    }

    @Test
    void a_credential_expiring_before_the_appointment_date_is_not_enough() throws SQLException {
        sql("UPDATE professional_credential SET licensed_until = '" + tuesday.minusDays(2) + "' WHERE user_id = " + dentist);

        assertThatThrownBy(() -> book(dentist, "10:00", 30))
            .isInstanceOf(DentistNotSchedulableException.class)
            .extracting("reason").isEqualTo(ClinicalCapacityState.EXPIRED);
    }

    @Test
    void a_user_who_is_not_a_member_is_not_schedulable() throws SQLException {
        long outsider;
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             var rs = s.executeQuery("INSERT INTO users (email, password_hash) VALUES ('out-" + UUID.randomUUID()
                 + "@test.local', 'x') RETURNING id")) {
            rs.next();
            outsider = rs.getLong(1);
        }

        assertThatThrownBy(() -> book(outsider, "10:00", 30))
            .isInstanceOf(DentistNotSchedulableException.class)
            .extracting("reason").isNull();
    }

    @Test
    void the_schedulable_list_holds_only_enabled_members() throws SQLException {
        long noCredential = member(false);

        assertThat(appointmentService.schedulableDentists())
            .extracting(m -> m.userId())
            .contains(dentist, secondDentist)
            .doesNotContain(noCredential);
    }

    // --- Sin choques en la agenda del odontólogo ---

    @Test
    void a_partial_overlap_with_the_same_dentist_is_a_conflict() {
        book(dentist, "10:00", 30);

        assertThatThrownBy(() -> book(dentist, "10:15", 30)).isInstanceOf(AppointmentConflictException.class);
        assertThat(appointmentRepository.count()).isEqualTo(1);
    }

    @Test
    void contiguous_appointments_are_fine() {
        book(dentist, "10:00", 30);

        assertThat(book(dentist, "10:30", 30).status()).isEqualTo(AppointmentStatus.AGENDADA);
    }

    @Test
    void a_cancelled_appointment_frees_its_slot() {
        AppointmentView first = book(dentist, "10:00", 30);
        appointmentService.cancel(first.id(), null);

        assertThat(book(dentist, "10:00", 30).status()).isEqualTo(AppointmentStatus.AGENDADA);
    }

    @Test
    void another_dentist_can_take_the_same_slot() {
        book(dentist, "10:00", 30);

        assertThat(book(secondDentist, "10:00", 30).status()).isEqualTo(AppointmentStatus.AGENDADA);
    }

    // --- Cita dentro del horario de atención ---

    private void tuesdayWithSplitShift() {
        BusinessHours current = businessHoursService.current().hours();
        Map<DayOfWeek, List<BusinessHours.Segment>> week = current.asMap();
        week.put(DayOfWeek.TUESDAY, List.of(
            new BusinessHours.Segment(LocalTime.of(9, 0), LocalTime.of(13, 0)),
            new BusinessHours.Segment(LocalTime.of(15, 0), LocalTime.of(19, 0))));
        businessHoursService.replace(BusinessHours.of(week));
    }

    @Test
    void an_appointment_exceeding_closing_is_outside_hours() {
        tuesdayWithSplitShift();

        assertThatThrownBy(() -> book(dentist, "12:45", 30))
            .isInstanceOf(OutsideBusinessHoursException.class)
            .hasMessageContaining("fuera del horario");
    }

    @Test
    void an_appointment_crossing_the_split_shift_is_outside_hours() {
        tuesdayWithSplitShift();

        assertThatThrownBy(() -> book(dentist, "12:30", 180)).isInstanceOf(OutsideBusinessHoursException.class);
    }

    @Test
    void a_closed_day_is_rejected_as_such() {
        LocalDate sunday = tuesday.minusDays(2);

        assertThatThrownBy(() -> appointmentService.schedule(new ScheduleData(patient, dentist, at(sunday, "10:00"), 30, null)))
            .isInstanceOf(OutsideBusinessHoursException.class)
            .hasMessageContaining("no atiende");
    }

    @Test
    void the_rules_are_evaluated_in_the_clinic_zone_not_the_jvm_one() throws SQLException {
        ZoneId auckland = ZoneId.of("Pacific/Auckland");
        sql("UPDATE organization_settings SET timezone = 'Pacific/Auckland' WHERE organization_id = " + org);
        Instant tenInAuckland = tuesday.atTime(10, 0).atZone(auckland).toInstant();
        Instant tenInUtc = tuesday.atTime(10, 0).atZone(ZoneId.of("UTC")).toInstant();

        AppointmentView created = appointmentService.schedule(new ScheduleData(patient, dentist, tenInAuckland, 30, null));
        assertThat(created.startsAt()).isEqualTo(tenInAuckland);
        // 10:00 UTC son las 22:00 o 23:00 de Auckland: fuera del horario de la clínica.
        assertThatThrownBy(() -> appointmentService.schedule(new ScheduleData(patient, secondDentist, tenInUtc, 30, null)))
            .isInstanceOf(OutsideBusinessHoursException.class);
        // La semana también es la de Auckland: el martes civil de la clínica contiene la cita.
        assertThat(appointmentService.list(tuesday, tuesday, null)).extracting(AppointmentView::id)
            .containsExactly(created.id());
    }

    @Test
    void changing_the_hours_does_not_touch_existing_appointments() {
        LocalDate saturday = tuesday.plusDays(4);
        AppointmentView booked = appointmentService.schedule(
            new ScheduleData(patient, dentist, at(saturday, "12:00"), 30, null));
        Map<DayOfWeek, List<BusinessHours.Segment>> week = businessHoursService.current().hours().asMap();
        week.put(DayOfWeek.SATURDAY, List.of(new BusinessHours.Segment(LocalTime.of(9, 0), LocalTime.of(12, 0))));

        businessHoursService.replace(BusinessHours.of(week));

        assertThat(appointmentService.get(booked.id()).status()).isEqualTo(AppointmentStatus.AGENDADA);
        assertThat(appointmentService.list(saturday, saturday, null)).extracting(AppointmentView::id)
            .containsExactly(booked.id());
        // Reprogramarla exige el horario vigente: el mismo hueco ya no entra.
        assertThatThrownBy(() -> appointmentService.reschedule(booked.id(),
                new RescheduleData(dentist, at(saturday, "12:00"), 30)))
            .isInstanceOf(OutsideBusinessHoursException.class);
    }

    @Test
    void a_range_longer_than_seven_days_or_inverted_is_rejected() {
        assertThatThrownBy(() -> appointmentService.list(tuesday, tuesday.plusDays(7), null))
            .isInstanceOf(InvalidAppointmentScheduleException.class);
        assertThatThrownBy(() -> appointmentService.list(tuesday, tuesday.minusDays(1), null))
            .isInstanceOf(InvalidAppointmentScheduleException.class);
    }

    @Test
    void the_week_can_be_filtered_by_dentist() {
        AppointmentView mine = book(dentist, "10:00", 30);
        book(secondDentist, "10:00", 30);

        assertThat(appointmentService.list(tuesday, tuesday, dentist)).extracting(AppointmentView::id)
            .containsExactly(mine.id());
    }

    // --- Reprogramar una cita agendada ---

    @Test
    void rescheduling_moves_the_same_appointment_to_another_day() {
        AppointmentView original = book(dentist, "10:00", 30);
        LocalDate thursday = tuesday.plusDays(2);

        AppointmentView moved = appointmentService.reschedule(original.id(),
            new RescheduleData(dentist, at(thursday, "16:00"), 30));

        assertThat(moved.id()).isEqualTo(original.id());
        assertThat(moved.patientId()).isEqualTo(patient);
        assertThat(moved.status()).isEqualTo(AppointmentStatus.AGENDADA);
        assertThat(moved.startsAt()).isEqualTo(at(thursday, "16:00"));
    }

    @Test
    void extending_an_appointment_over_its_own_slot_is_not_a_conflict() {
        AppointmentView original = book(dentist, "10:00", 30);

        AppointmentView longer = appointmentService.reschedule(original.id(),
            new RescheduleData(dentist, at(tuesday, "10:00"), 60));

        assertThat(longer.endsAt()).isEqualTo(at(tuesday, "11:00"));
    }

    @Test
    void rescheduling_into_an_occupied_slot_leaves_the_appointment_as_it_was() {
        AppointmentView original = book(dentist, "10:00", 30);
        book(dentist, "11:00", 30);

        assertThatThrownBy(() -> appointmentService.reschedule(original.id(),
                new RescheduleData(dentist, at(tuesday, "11:15"), 30)))
            .isInstanceOf(AppointmentConflictException.class);
        assertThat(appointmentService.get(original.id()).startsAt()).isEqualTo(at(tuesday, "10:00"));
    }

    @Test
    void a_closed_appointment_cannot_be_rescheduled() {
        AppointmentView original = book(dentist, "10:00", 30);
        appointmentService.cancel(original.id(), null);

        assertThatThrownBy(() -> appointmentService.reschedule(original.id(),
                new RescheduleData(dentist, at(tuesday, "11:00"), 30)))
            .isInstanceOf(AppointmentClosedException.class);
    }

    // --- Ciclo de estados de la cita ---

    @Test
    void cancelling_records_the_reason_who_and_when() {
        AppointmentView original = book(dentist, "10:00", 30);

        AppointmentView cancelled = appointmentService.cancel(original.id(), "El paciente pidió otro día");

        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELADA);
        assertThat(cancelled.cancellationReason()).isEqualTo("El paciente pidió otro día");
        assertThat(cancelled.statusChangedBy()).isEqualTo(someUser);
        assertThat(cancelled.statusChangedAt()).isNotNull();
    }

    @Test
    void attendance_cannot_be_marked_before_the_start() {
        AppointmentView future = book(dentist, "10:00", 30);

        assertThatThrownBy(() -> appointmentService.markAttended(future.id()))
            .isInstanceOf(AppointmentNotStartedException.class);
        assertThatThrownBy(() -> appointmentService.markNoShow(future.id()))
            .isInstanceOf(AppointmentNotStartedException.class);
    }

    @Test
    void a_patient_who_did_not_come_is_marked_no_show() throws SQLException {
        long started = startedAppointment(patient);

        assertThat(appointmentService.markNoShow(started).status()).isEqualTo(AppointmentStatus.NO_ASISTIO);
    }

    @Test
    void a_final_status_does_not_change() throws SQLException {
        long started = startedAppointment(patient);
        appointmentService.markAttended(started);

        assertThatThrownBy(() -> appointmentService.cancel(started, null)).isInstanceOf(AppointmentClosedException.class);
        assertThatThrownBy(() -> appointmentService.markNoShow(started)).isInstanceOf(AppointmentClosedException.class);
        assertThatThrownBy(() -> appointmentService.markAttended(started)).isInstanceOf(AppointmentClosedException.class);
    }

    @Test
    void a_missing_appointment_is_not_found() {
        assertThatThrownBy(() -> appointmentService.get(999_999L)).isInstanceOf(AppointmentNotFoundException.class);
    }

    // --- Atender la cita con nota de consulta ---

    @Test
    void attending_with_a_note_opens_and_links_it() throws SQLException {
        long started = startedAppointment(patient);

        AppointmentView attended = appointmentService.attendWithNote(started);

        assertThat(attended.status()).isEqualTo(AppointmentStatus.ATENDIDA);
        var note = noteRepository.findById(attended.consultationNoteId()).orElseThrow();
        assertThat(note.getStatus()).isEqualTo(ConsultationNoteStatus.ABIERTA);
        assertThat(note.getAttendedAt()).isEqualTo(attended.startsAt());
    }

    @Test
    void without_clinical_capacity_the_caller_cannot_attend_with_a_note() throws SQLException {
        long started = startedAppointment(patient);
        long receptionist = member(false);
        asUser(receptionist);

        assertThatThrownBy(() -> appointmentService.attendWithNote(started))
            .isInstanceOf(ClinicalCapacityRequiredException.class);
        assertUnchanged(started);
    }

    @Test
    void with_revoked_or_expired_capacity_the_caller_cannot_attend_with_a_note() throws SQLException {
        long started = startedAppointment(patient);
        revokeAsAdmin(someUser);
        assertThatThrownBy(() -> appointmentService.attendWithNote(started))
            .isInstanceOf(ClinicalCapacityRequiredException.class);

        asUser(otherUser);
        sql("UPDATE professional_credential SET licensed_until = current_date - 1 WHERE user_id = " + otherUser);
        assertThatThrownBy(() -> appointmentService.attendWithNote(started))
            .isInstanceOf(ClinicalCapacityRequiredException.class);
        assertUnchanged(started);
    }

    @Test
    void a_patient_without_a_completed_history_cannot_be_attended_with_a_note() throws SQLException {
        long started = startedAppointment(aPatient());

        assertThatThrownBy(() -> appointmentService.attendWithNote(started))
            .isInstanceOf(ClinicalHistoryNotCompletedException.class);
        assertUnchanged(started);
    }

    @Test
    void an_already_open_note_blocks_attending_with_another() throws SQLException {
        noteService.open(patient, hoursAgo(2));
        long started = startedAppointment(patient);

        assertThatThrownBy(() -> appointmentService.attendWithNote(started))
            .isInstanceOf(ConsultationNoteAlreadyOpenException.class);
        assertThat(appointmentRepository.findById(started).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.AGENDADA);
    }

    @Test
    void an_appointment_already_linked_or_not_started_opens_no_other_note() throws SQLException {
        long started = startedAppointment(patient);
        appointmentService.attendWithNote(started);
        assertThatThrownBy(() -> appointmentService.attendWithNote(started))
            .isInstanceOf(AppointmentNoteAlreadyLinkedException.class);

        AppointmentView future = book(dentist, "10:00", 30);
        assertThatThrownBy(() -> appointmentService.attendWithNote(future.id()))
            .isInstanceOf(AppointmentNotStartedException.class);
        assertThat(noteRepository.count()).isEqualTo(1);
    }

    @Test
    void a_discarded_note_unlinks_the_appointment_which_can_be_attended_again() throws SQLException {
        long started = startedAppointment(patient);
        Long noteId = appointmentService.attendWithNote(started).consultationNoteId();

        noteService.discard(patient, noteId);

        AppointmentView unlinked = appointmentService.get(started);
        assertThat(unlinked.status()).isEqualTo(AppointmentStatus.ATENDIDA);
        assertThat(unlinked.consultationNoteId()).isNull();
        assertThat(appointmentService.attendWithNote(started).consultationNoteId()).isNotNull();
    }

    @Test
    void an_appointment_marked_attended_without_a_note_can_be_attended_with_one_later() throws SQLException {
        long started = startedAppointment(patient);

        AppointmentView attended = appointmentService.markAttended(started);
        assertThat(attended.consultationNoteId()).isNull();

        assertThat(appointmentService.attendWithNote(started).consultationNoteId()).isNotNull();
    }

    private void assertUnchanged(long appointmentId) {
        var appointment = appointmentRepository.findById(appointmentId).orElseThrow();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.AGENDADA);
        assertThat(appointment.getConsultationNoteId()).isNull();
        assertThat(noteRepository.count()).isZero();
    }
}
