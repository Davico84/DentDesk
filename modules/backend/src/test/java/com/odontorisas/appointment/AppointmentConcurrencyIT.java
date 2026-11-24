package com.odontorisas.appointment;

import com.odontorisas.common.AppointmentStatus;
import com.odontorisas.consultationnote.AbstractConsultationNoteIT;
import com.odontorisas.persistence.repository.AppointmentRepository;
import com.odontorisas.persistence.repository.ConsultationNoteRepository;
import com.odontorisas.service.appointment.AppointmentConflictException;
import com.odontorisas.service.appointment.AppointmentService;
import com.odontorisas.service.appointment.AppointmentService.ScheduleData;
import com.odontorisas.service.appointment.AppointmentView;
import com.odontorisas.service.staff.ClinicalCapacityRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Carreras de la agenda con hilos reales contra PostgreSQL (add-appointments tareas 3.6 y 3.8). Cada hilo con su
 * pool propio (nunca {@code supplyAsync} sin executor: en CI hay un solo hilo común). Las carreras con orden
 * importan se fuerzan: una transacción JDBC toma el bloqueo, el servicio corre en otro hilo y la primera confirma
 * recién cuando el servicio quedó esperando ({@code pg_locks}).
 */
@SpringBootTest
class AppointmentConcurrencyIT extends AbstractConsultationNoteIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    AppointmentService appointmentService;
    @Autowired
    AppointmentRepository appointmentRepository;
    @Autowired
    ConsultationNoteRepository noteRepository;

    private long admin;
    private long patient;

    @BeforeEach
    void seedSchedule() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("INSERT INTO organization_settings (organization_id) VALUES (" + org + ")");
            try (ResultSet rs = s.executeQuery("INSERT INTO users (email, password_hash, full_name) VALUES ('adm-"
                    + UUID.randomUUID() + "@test.local', 'x', 'Admin') RETURNING id")) {
                rs.next();
                admin = rs.getLong(1);
            }
            s.execute("INSERT INTO memberships (user_id, organization_id, role, status) VALUES (" + admin + ", " + org
                + ", 'ADMIN', 'ACTIVE')");
        }
        patient = aPatientWithSealedHistory();
    }

    private Instant nextTuesdayAt10() {
        return LocalDate.now(LIMA).with(TemporalAdjusters.next(DayOfWeek.TUESDAY)).plusWeeks(1)
            .atTime(10, 0).atZone(LIMA).toInstant();
    }

    /** Cita que empezó hace 10 minutos (sembrada: no se agenda en el pasado). */
    private long startedAppointment() throws SQLException {
        Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("INSERT INTO appointments (organization_id, patient_id, dentist_user_id, "
                 + "starts_at, ends_at, created_by, updated_by) VALUES (" + org + ", " + patient + ", " + someUser + ", '"
                 + start + "', '" + start.plus(30, ChronoUnit.MINUTES) + "', " + someUser + ", " + someUser
                 + ") RETURNING id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Corre {@code work} en un hilo propio con la sesión de {@code someUser}; devuelve lo que lanzó o {@code null}. */
    private Future<Throwable> inThread(ExecutorService pool, CountDownLatch start, Callable<?> work) {
        return pool.submit(() -> {
            asUser(someUser);
            try {
                start.await();
                work.call();
                return (Throwable) null;
            } catch (Throwable e) {
                return e;
            } finally {
                clearContext();
            }
        });
    }

    /** Espera a que alguna sesión quede esperando un bloqueo (el servicio ya está bloqueado). */
    private void awaitSomeoneBlocked() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
                 ResultSet rs = s.executeQuery("SELECT count(*) FROM pg_locks WHERE NOT granted")) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    return;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("El servicio nunca quedó esperando el bloqueo");
    }

    // --- 3.8: Sin choques ante reservas simultáneas ---

    @Test
    void two_simultaneous_bookings_of_the_same_slot_create_exactly_one() throws Exception {
        Instant slot = nextTuesdayAt10();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Throwable> outcomes = new ArrayList<>();
        try {
            List<Future<Throwable>> bookings = List.of(
                inThread(pool, start, () -> appointmentService.schedule(new ScheduleData(patient, someUser, slot, 30, null))),
                inThread(pool, start, () -> appointmentService.schedule(new ScheduleData(patient, someUser, slot.plus(15,
                    ChronoUnit.MINUTES), 30, null))));
            start.countDown();
            for (Future<Throwable> booking : bookings) {
                outcomes.add(booking.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(outcomes).filteredOn(e -> e == null).hasSize(1);
        assertThat(outcomes).filteredOn(e -> e != null).singleElement().isInstanceOf(AppointmentConflictException.class);
        assertThat(appointmentRepository.count()).isEqualTo(1);
    }

    // --- 3.6: La clínica retira la capacidad mientras se atiende con nota ---

    @Test
    void a_revocation_committed_first_rejects_attending_with_a_note() throws Exception {
        long appointment = startedAppointment();
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try (Connection revocation = ownerConnection(); Statement s = revocation.createStatement()) {
            revocation.setAutoCommit(false);
            s.execute("SELECT set_config('app.user_id', '" + admin + "', true)");
            s.execute("UPDATE memberships SET clinical_capacity_revoked_at = now(), clinical_capacity_revoked_by = "
                + admin + " WHERE user_id = " + someUser + " AND organization_id = " + org);

            Future<Throwable> attend = inThread(pool, new CountDownLatch(0), () -> appointmentService.attendWithNote(appointment));
            awaitSomeoneBlocked();
            revocation.commit();

            assertThat(attend.get(30, TimeUnit.SECONDS))
                .as("lee la membresía FOR SHARE: espera al retiro y lo ve")
                .isInstanceOf(ClinicalCapacityRequiredException.class);
        } finally {
            pool.shutdownNow();
        }
        assertThat(appointmentRepository.findById(appointment).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.AGENDADA);
        assertThat(noteRepository.count()).isZero();
    }

    @Test
    void attending_with_a_note_first_wins_and_the_revocation_waits() throws Exception {
        long appointment = startedAppointment();

        AppointmentView attended = appointmentService.attendWithNote(appointment);
        try (Connection revocation = ownerConnection(); Statement s = revocation.createStatement()) {
            s.execute("SELECT set_config('app.user_id', '" + admin + "', false)");
            s.execute("UPDATE memberships SET clinical_capacity_revoked_at = now(), clinical_capacity_revoked_by = "
                + admin + " WHERE user_id = " + someUser + " AND organization_id = " + org);
        }

        assertThat(attended.consultationNoteId()).isNotNull();
        assertThat(appointmentService.get(appointment).consultationNoteId()).isEqualTo(attended.consultationNoteId());
    }

    // --- 3.6: Descartar la nota mientras se atiende con nota (design.md D7, orden de bloqueos) ---

    @Test
    void discarding_the_linked_note_while_attending_again_does_not_deadlock() throws Exception {
        long appointment = startedAppointment();
        Long firstNote = appointmentService.attendWithNote(appointment).consultationNoteId();
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try (Connection discard = ownerConnection(); Statement s = discard.createStatement()) {
            discard.setAutoCommit(false);
            // El descarte borra la nota; el ON DELETE SET NULL escribe (y bloquea) la cita enlazada.
            s.execute("DELETE FROM consultation_note WHERE id = " + firstNote);

            Future<Throwable> attendAgain = inThread(pool, new CountDownLatch(0),
                () -> appointmentService.attendWithNote(appointment));
            awaitSomeoneBlocked();
            discard.commit();

            assertThat(attendAgain.get(30, TimeUnit.SECONDS)).as("espera al descarte y abre otra nota, sin 40P01").isNull();
        } finally {
            pool.shutdownNow();
        }
        Long linked = appointmentService.get(appointment).consultationNoteId();
        assertThat(linked).isNotNull().isNotEqualTo(firstNote);
        assertThat(noteRepository.count()).isEqualTo(1);
    }

    @Test
    void discarding_waits_for_the_linked_appointment_before_locking_the_note() throws Exception {
        long appointment = startedAppointment();
        Long note = appointmentService.attendWithNote(appointment).consultationNoteId();
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try (Connection attending = ownerConnection(); Statement s = attending.createStatement()) {
            // Como "atender con nota" en curso: la cita ya está tomada.
            attending.setAutoCommit(false);
            s.execute("SELECT id FROM appointments WHERE id = " + appointment + " FOR UPDATE");

            Future<Throwable> discard = inThread(pool, new CountDownLatch(0), () -> {
                noteService.discard(patient, note);
                return null;
            });
            awaitSomeoneBlocked();
            // El descarte espera la cita SIN haber bloqueado la nota: mismo orden cita -> nota que "atender con nota".
            try (Connection probe = ownerConnection(); Statement p = probe.createStatement()) {
                probe.setAutoCommit(false);
                p.execute("SELECT id FROM consultation_note WHERE id = " + note + " FOR UPDATE NOWAIT");
                probe.rollback();
            }
            attending.commit();

            assertThat(discard.get(30, TimeUnit.SECONDS)).as("termina sin 40P01").isNull();
        } finally {
            pool.shutdownNow();
        }
        assertThat(appointmentService.get(appointment).consultationNoteId()).isNull();
        assertThat(noteRepository.count()).isZero();
    }
}
