package com.odontorisas.staff;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.PatientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La foto de la credencial es obligatoria en todo sellado nuevo, y lo sellado antes queda intacto
 * ({@code update-staff-professional-credential} design.md D5, migración V30: {@code CHECK ... NOT
 * VALID}). Por SQL crudo con la conexión owner, que no pasa por ningún servicio: ahí la única barrera
 * es la base.
 *
 * <p>Scenarios: <i>Un sellado nuevo sin la foto es imposible</i> ({@code clinical-history} y
 * {@code dental-chart}), <i>Lo sellado antes del cambio no se reescribe</i> y <i>Una Historia sellada
 * antes del cambio no se modifica ni en la base</i>.
 */
@SpringBootTest
class SignerSnapshotConstraintIT extends AbstractTenantAwareIntegrationTest {

    private static final String SIGNER =
        ", signer_full_name = 'Dra. Prueba', signer_cop_number = '12345', signer_licensed_until = current_date";

    private long org;
    private long someUser;

    @Autowired
    private PatientRepository patientRepository;

    @BeforeEach
    void aClinicAndAUser() throws SQLException {
        String suffix = UUID.randomUUID().toString();
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            org = id(s, "INSERT INTO organizations (name, slug) VALUES ('Firma', 'firma-" + suffix + "') RETURNING id");
            someUser = id(s, "INSERT INTO users (email, password_hash) VALUES ('firma-" + suffix
                + "@test.local', 'x') RETURNING id");
        }
        IdentityContext.set(someUser);
        OrganizationContext.set(org);
    }

    @AfterEach
    void clearContext() {
        IdentityContext.clear();
        OrganizationContext.clear();
    }

    private static long id(Statement s, String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private long aPatient() {
        return patientRepository.save(Patient.builder()
            .fullName("Paciente firma")
            .birthDate(LocalDate.of(1980, 1, 1))
            .status(PatientStatus.ACTIVE)
            .allergies(List.of())
            .conditions(List.of())
            .currentMedication(List.of())
            .build()).getId();
    }

    private long draftHistoria(Statement s, long patient) throws SQLException {
        return id(s, "INSERT INTO historia_clinica (organization_id, patient_id, created_by, updated_by) VALUES ("
            + org + ", " + patient + ", " + someUser + ", " + someUser + ") RETURNING id");
    }

    private long openBlock(Statement s, long patient) throws SQLException {
        return id(s, "INSERT INTO dental_chart_block (organization_id, patient_id, examined_on, created_by, updated_by) "
            + "VALUES (" + org + ", " + patient + ", current_date, " + someUser + ", " + someUser + ") RETURNING id");
    }

    private long openNote(Statement s, long patient) throws SQLException {
        return id(s, "INSERT INTO consultation_note (organization_id, patient_id, attended_at, reason, examination, "
            + "treatment, created_by, updated_by) VALUES (" + org + ", " + patient + ", now(), 'Dolor', 'Caries 36', "
            + "'Restauración', " + someUser + ", " + someUser + ") RETURNING id");
    }

    private static void assertCheckViolation(Statement s, String sql) {
        assertThatThrownBy(() -> s.execute(sql))
            .isInstanceOf(SQLException.class)
            .satisfies(e -> assertThat(((SQLException) e).getSQLState())
                .as("check_violation, de los CHECK de la foto de V30").isEqualTo("23514"));
    }

    /**
     * Pone una fila en el estado "sellada antes de V30": suspende la restricción, ejecuta el SQL y la
     * vuelve a crear con su misma definición ({@code pg_get_constraintdef} ya trae {@code NOT VALID}),
     * todo en una transacción. Es la forma de fabricar en el test una fila que existía antes de la
     * restricción.
     */
    private void asBeforeTheConstraint(String table, String constraint, String sql) throws SQLException {
        try (Connection owner = ownerConnection()) {
            owner.setAutoCommit(false);
            try (Statement s = owner.createStatement()) {
                String definition;
                try (ResultSet rs = s.executeQuery("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = '"
                    + constraint + "'")) {
                    rs.next();
                    definition = rs.getString(1);
                }
                s.execute("ALTER TABLE " + table + " DROP CONSTRAINT " + constraint);
                s.execute(sql);
                s.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + constraint + " " + definition);
                owner.commit();
            } catch (SQLException e) {
                owner.rollback();
                throw e;
            }
        }
    }

    // --- Un sellado nuevo sin la foto es imposible ---------------------------------------------

    @Test
    void a_clinical_history_cannot_be_completed_without_the_signer() throws SQLException {
        long patient = aPatient();
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            long historia = draftHistoria(s, patient);
            assertCheckViolation(s, "UPDATE historia_clinica SET status = 'COMPLETED', completed_at = now() "
                + "WHERE id = " + historia);
            assertCheckViolation(s, "UPDATE historia_clinica SET status = 'COMPLETED', completed_at = now()" + SIGNER
                + " WHERE id = " + historia);
            s.execute("UPDATE historia_clinica SET status = 'COMPLETED', completed_at = now(), completed_by = "
                + someUser + SIGNER + " WHERE id = " + historia);
        }
    }

    @Test
    void a_dental_chart_cannot_be_sealed_without_the_signer() throws SQLException {
        long patient = aPatient();
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            long block = openBlock(s, patient);
            assertCheckViolation(s, "UPDATE dental_chart_block SET status = 'SEALED', sealed_at = now() WHERE id = "
                + block);
            s.execute("UPDATE dental_chart_block SET status = 'SEALED', sealed_at = now(), sealed_by = " + someUser
                + SIGNER + " WHERE id = " + block);
        }
    }

    @Test
    void a_consultation_note_cannot_be_sealed_without_the_signer() throws SQLException {
        long patient = aPatient();
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            long note = openNote(s, patient);
            assertCheckViolation(s, "UPDATE consultation_note SET status = 'SELLADA', sealed_at = now(), sealed_by = "
                + someUser + " WHERE id = " + note);
            s.execute("UPDATE consultation_note SET status = 'SELLADA', sealed_at = now(), sealed_by = " + someUser
                + SIGNER + " WHERE id = " + note);
        }
    }

    // --- Lo sellado antes del cambio -----------------------------------------------------------

    @Test
    void a_history_sealed_before_the_change_stays_readable_and_cannot_be_modified() throws SQLException {
        long patient = aPatient();
        long historia;
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            historia = draftHistoria(s, patient);
        }
        asBeforeTheConstraint("historia_clinica", "historia_clinica_signer_required",
            "UPDATE historia_clinica SET status = 'COMPLETED', completed_at = now() WHERE id = " + historia);

        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT status, signer_cop_number FROM historia_clinica WHERE id = "
                + historia)) {
                rs.next();
                assertThat(rs.getString(1)).isEqualTo("COMPLETED");
                assertThat(rs.getString(2)).isNull();
            }
            // Sin trigger de sellado propio, la Historia vieja gana igual una guarda en la base: el
            // CHECK se reevalúa en cada UPDATE de la fila (design.md D5).
            assertCheckViolation(s, "UPDATE historia_clinica SET motivo_consulta = 'otro' WHERE id = " + historia);
        }
    }

    @Test
    void a_note_and_a_dental_chart_sealed_before_the_change_stay_as_they_were() throws SQLException {
        long patient = aPatient();
        long note;
        long block;
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            note = openNote(s, patient);
            block = openBlock(s, patient);
        }
        asBeforeTheConstraint("consultation_note", "consultation_note_signer_required",
            "UPDATE consultation_note SET status = 'SELLADA', sealed_at = now(), sealed_by = " + someUser
                + " WHERE id = " + note);
        asBeforeTheConstraint("dental_chart_block", "dental_chart_block_signer_required",
            "UPDATE dental_chart_block SET status = 'SEALED', sealed_at = now() WHERE id = " + block);

        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT status, signer_cop_number FROM consultation_note WHERE id = "
                + note)) {
                rs.next();
                assertThat(rs.getString(1)).isEqualTo("SELLADA");
                assertThat(rs.getString(2)).isNull();
            }
            try (ResultSet rs = s.executeQuery("SELECT status, sealed_by FROM dental_chart_block WHERE id = " + block)) {
                rs.next();
                assertThat(rs.getString(1)).isEqualTo("SEALED");
                assertThat(rs.getObject(2)).isNull();
            }
        }
    }

    @Test
    void the_signer_constraints_stay_not_valid() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT conname, convalidated FROM pg_constraint WHERE conname IN ("
                 + "'historia_clinica_signer_required', 'dental_chart_block_signer_required', "
                 + "'consultation_note_signer_required')")) {
            int count = 0;
            while (rs.next()) {
                count++;
                assertThat(rs.getBoolean(2)).as(rs.getString(1)).isFalse();
            }
            assertThat(count).isEqualTo(3);
        }
    }
}
