package com.odontorisas.appointment;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Garantías de la tabla {@code appointments} que viven en la base (add-appointments design.md D1, D2, D7,
 * V32), probadas con SQL crudo: lo que una escritura por fuera del servicio tampoco puede romper.
 *
 * <p>Las restricciones se prueban por la conexión owner (salta RLS, no los CHECK ni la exclusión); la RLS,
 * por el rol de aplicación con {@code app.organization_id} fijado.
 */
@SpringBootTest
class AppointmentDatabaseIT extends AbstractTenantAwareIntegrationTest {

    private long orgA;
    private long orgB;
    private long user;
    private long dentist;
    private long otherDentist;
    private long patientA;
    private long patientB;

    @BeforeEach
    void seed() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("TRUNCATE appointments, consultation_note, patients RESTART IDENTITY CASCADE");
            String suffix = UUID.randomUUID().toString();
            orgA = id(s, "INSERT INTO organizations (name, slug) VALUES ('A', 'ap-a-" + suffix + "') RETURNING id");
            orgB = id(s, "INSERT INTO organizations (name, slug) VALUES ('B', 'ap-b-" + suffix + "') RETURNING id");
            user = id(s, "INSERT INTO users (email, password_hash) VALUES ('ap-" + suffix + "@test.local', 'x') RETURNING id");
            dentist = id(s, "INSERT INTO users (email, password_hash) VALUES ('ap-d1-" + suffix + "@test.local', 'x') RETURNING id");
            otherDentist = id(s, "INSERT INTO users (email, password_hash) VALUES ('ap-d2-" + suffix + "@test.local', 'x') RETURNING id");
            patientA = id(s, patientInsert(orgA));
            patientB = id(s, patientInsert(orgB));
        }
    }

    private static long id(Statement s, String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String patientInsert(long org) {
        return "INSERT INTO patients (organization_id, full_name, birth_date, status, created_by, updated_by) VALUES ("
            + org + ", 'Paciente', '1990-01-01', 'ACTIVE', " + user + ", " + user + ") RETURNING id";
    }

    /** Cita AGENDADA de {@code from} a {@code to} (horas UTC del 2030-01-07). */
    private String appointment(long org, long patient, long dentistId, String from, String to) {
        return "INSERT INTO appointments (organization_id, patient_id, dentist_user_id, starts_at, ends_at, "
            + "created_by, updated_by) VALUES (" + org + ", " + patient + ", " + dentistId
            + ", '2030-01-07 " + from + "+00', '2030-01-07 " + to + "+00', " + user + ", " + user + ")";
    }

    private long insert(String sql) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            return id(s, sql + " RETURNING id");
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute(sql);
        }
    }

    private void close(long appointment, String status) throws SQLException {
        execute("UPDATE appointments SET status = '" + status + "', status_changed_at = now(), status_changed_by = "
            + user + " WHERE id = " + appointment);
    }

    @Test
    void overlapping_appointments_of_the_same_dentist_are_rejected() throws SQLException {
        insert(appointment(orgA, patientA, dentist, "10:00", "10:30"));

        assertThatThrownBy(() -> insert(appointment(orgA, patientA, dentist, "10:15", "10:45")))
            .isInstanceOf(SQLException.class)
            .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23P01"));
    }

    @Test
    void an_attended_appointment_still_blocks_its_slot() throws SQLException {
        long first = insert(appointment(orgA, patientA, dentist, "10:00", "10:30"));
        close(first, "ATENDIDA");

        assertThatThrownBy(() -> insert(appointment(orgA, patientA, dentist, "10:00", "10:30")))
            .isInstanceOf(SQLException.class);
    }

    @Test
    void contiguous_appointments_do_not_overlap() throws SQLException {
        insert(appointment(orgA, patientA, dentist, "10:00", "10:30"));

        insert(appointment(orgA, patientA, dentist, "10:30", "11:00"));
    }

    @Test
    void another_dentist_can_take_the_same_slot() throws SQLException {
        insert(appointment(orgA, patientA, dentist, "10:00", "10:30"));

        insert(appointment(orgA, patientA, otherDentist, "10:00", "10:30"));
    }

    @Test
    void cancelled_and_no_show_appointments_free_their_slot() throws SQLException {
        long cancelled = insert(appointment(orgA, patientA, dentist, "10:00", "10:30"));
        close(cancelled, "CANCELADA");
        long noShow = insert(appointment(orgA, patientA, dentist, "10:00", "10:30"));
        close(noShow, "NO_ASISTIO");

        insert(appointment(orgA, patientA, dentist, "10:00", "10:30"));
    }

    @Test
    void a_clinic_only_sees_its_own_appointments() throws SQLException {
        insert(appointment(orgA, patientA, dentist, "10:00", "10:30"));
        insert(appointment(orgB, patientB, dentist, "12:00", "12:30"));

        assertThat(visibleTo(orgA)).isEqualTo(1);
        assertThat(visibleTo(orgB)).isEqualTo(1);
    }

    private long visibleTo(long org) throws SQLException {
        try (Connection app = appConnection(); Statement s = app.createStatement()) {
            app.setAutoCommit(false);
            s.execute("SET LOCAL app.organization_id = '" + org + "'");
            try (ResultSet rs = s.executeQuery("SELECT count(*) FROM appointments WHERE organization_id = " + org
                    + " OR organization_id <> " + org)) {
                rs.next();
                long count = rs.getLong(1);
                app.rollback();
                return count;
            }
        }
    }

    @Test
    void a_patient_of_another_clinic_is_rejected_by_the_composite_key() {
        assertThatThrownBy(() -> insert(appointment(orgA, patientB, dentist, "10:00", "10:30")))
            .isInstanceOf(SQLException.class)
            .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23503"));
    }

    @Test
    void discarding_the_linked_note_unlinks_the_appointment_and_keeps_the_organization() throws SQLException {
        long appointment = insert(appointment(orgA, patientA, dentist, "10:00", "10:30"));
        long note = insert("INSERT INTO consultation_note (organization_id, patient_id, attended_at, created_by, "
            + "updated_by) VALUES (" + orgA + ", " + patientA + ", '2030-01-07 10:00+00', " + user + ", " + user + ")");
        execute("UPDATE appointments SET status = 'ATENDIDA', status_changed_at = now(), status_changed_by = " + user
            + ", consultation_note_id = " + note + " WHERE id = " + appointment);

        execute("DELETE FROM consultation_note WHERE id = " + note);

        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT consultation_note_id, organization_id, status FROM appointments WHERE id = "
                 + appointment)) {
            rs.next();
            assertThat(rs.getObject(1)).isNull();
            assertThat(rs.getLong(2)).isEqualTo(orgA);
            assertThat(rs.getString(3)).isEqualTo("ATENDIDA");
        }
    }

    @Test
    void a_cancellation_reason_outside_a_cancelled_appointment_is_rejected() throws SQLException {
        long appointment = insert(appointment(orgA, patientA, dentist, "10:00", "10:30"));

        assertThatThrownBy(() -> execute("UPDATE appointments SET cancellation_reason = 'x', status = 'NO_ASISTIO', "
            + "status_changed_at = now(), status_changed_by = " + user + " WHERE id = " + appointment))
            .isInstanceOf(SQLException.class)
            .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"));
    }

    @Test
    void a_note_on_an_appointment_that_is_not_attended_is_rejected() throws SQLException {
        long appointment = insert(appointment(orgA, patientA, dentist, "10:00", "10:30"));
        long note = insert("INSERT INTO consultation_note (organization_id, patient_id, attended_at, created_by, "
            + "updated_by) VALUES (" + orgA + ", " + patientA + ", '2030-01-07 10:00+00', " + user + ", " + user + ")");

        assertThatThrownBy(() -> execute("UPDATE appointments SET consultation_note_id = " + note + " WHERE id = "
            + appointment))
            .isInstanceOf(SQLException.class)
            .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"));
    }

    @Test
    void a_final_status_without_who_and_when_is_rejected() throws SQLException {
        long appointment = insert(appointment(orgA, patientA, dentist, "10:00", "10:30"));

        assertThatThrownBy(() -> execute("UPDATE appointments SET status = 'CANCELADA' WHERE id = " + appointment))
            .isInstanceOf(SQLException.class)
            .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"));
    }

    @Test
    void a_scheduled_appointment_with_a_status_change_is_rejected() {
        assertThatThrownBy(() -> insert("INSERT INTO appointments (organization_id, patient_id, dentist_user_id, "
            + "starts_at, ends_at, status_changed_at, status_changed_by, created_by, updated_by) VALUES (" + orgA + ", "
            + patientA + ", " + dentist + ", '2030-01-07 10:00+00', '2030-01-07 10:30+00', now(), " + user + ", "
            + user + ", " + user + ")"))
            .isInstanceOf(SQLException.class)
            .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"));
    }

    @Test
    void the_default_business_hours_cover_existing_clinics() throws SQLException {
        execute("INSERT INTO organization_settings (organization_id) VALUES (" + orgA + ")");

        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT business_hours->'MONDAY'->0->>'opens', "
                 + "business_hours->'SATURDAY'->0->>'closes', jsonb_array_length(business_hours->'SUNDAY') "
                 + "FROM organization_settings WHERE organization_id = " + orgA)) {
            rs.next();
            assertThat(rs.getString(1)).isEqualTo("09:00");
            assertThat(rs.getString(2)).isEqualTo("13:00");
            assertThat(rs.getInt(3)).isZero();
        }
    }
}
