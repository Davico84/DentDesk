package com.odontorisas.consultationnote;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.persistence.repository.ClinicalBackgroundEntryRepository;
import com.odontorisas.persistence.repository.ConsultationNoteCorrectionRepository;
import com.odontorisas.persistence.repository.ConsultationNoteDiagnosisRepository;
import com.odontorisas.persistence.repository.ConsultationNoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aislamiento de <b>MOTOR</b> (RLS) para las <b>cuatro</b> tablas de la nota de consulta
 * (`update-clinical-history` design.md D1, V21). La RLS no se hereda por FK, así que cada tabla se
 * prueba por separado, y cada FK compuesta por tenant con su control (mismo patrón que
 * {@code DentalChartRlsIT}). Los datos se siembran por la conexión owner (se salta la RLS); las
 * lecturas van por los repositorios, con el rol de aplicación.
 */
@SpringBootTest
class ConsultationNoteRlsIT extends AbstractTenantAwareIntegrationTest {

    private long orgA;
    private long orgB;
    private long someUser;
    private long patientA;
    private long patientB;
    private long noteA;
    private long noteB;
    private long entryB;

    @Autowired
    private ConsultationNoteRepository noteRepository;
    @Autowired
    private ConsultationNoteDiagnosisRepository diagnosisRepository;
    @Autowired
    private ConsultationNoteCorrectionRepository correctionRepository;
    @Autowired
    private ClinicalBackgroundEntryRepository entryRepository;

    @BeforeEach
    void oneSealedNoteWithChildrenPerClinic() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("TRUNCATE consultation_note_correction, clinical_background_entry, consultation_note_diagnosis, "
                + "consultation_note, patients RESTART IDENTITY CASCADE");
            String suffix = UUID.randomUUID().toString();
            orgA = id(s, "INSERT INTO organizations (name, slug) VALUES ('A', 'cn-a-" + suffix + "') RETURNING id");
            orgB = id(s, "INSERT INTO organizations (name, slug) VALUES ('B', 'cn-b-" + suffix + "') RETURNING id");
            someUser = id(s, "INSERT INTO users (email, password_hash) VALUES ('cn-" + suffix
                + "@test.local', 'x') RETURNING id");
            patientA = id(s, patientInsert(orgA));
            patientB = id(s, patientInsert(orgB));
            noteA = seedSealedNote(s, orgA, patientA);
            noteB = seedSealedNote(s, orgB, patientB);
            entryB = id(s, "SELECT id FROM clinical_background_entry WHERE note_id = " + noteB);
        }
    }

    @AfterEach
    void clearContext() {
        IdentityContext.clear();
        OrganizationContext.clear();
    }

    private static long id(Statement s, String sql) throws SQLException {
        try (var rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String patientInsert(long org) {
        return "INSERT INTO patients (organization_id, full_name, birth_date, status, created_by, updated_by) VALUES ("
            + org + ", 'Paciente', '1990-01-01', 'ACTIVE', " + someUser + ", " + someUser + ") RETURNING id";
    }

    private String noteInsert(long org, long patient) {
        return "INSERT INTO consultation_note (organization_id, patient_id, attended_at, created_by, updated_by) "
            + "VALUES (" + org + ", " + patient + ", now(), " + someUser + ", " + someUser + ")";
    }

    private String diagnosisInsert(long org, long note) {
        return "INSERT INTO consultation_note_diagnosis (organization_id, note_id, cie10_code, ubicacion, "
            + "tipo_diagnostico, created_by, updated_by) SELECT " + org + ", " + note + ", code, 'GENERAL', "
            + "'DEFINITIVO', " + someUser + ", " + someUser + " FROM cie10_code LIMIT 1";
    }

    private String entryInsert(long org, long patient, long note) {
        return "INSERT INTO clinical_background_entry (organization_id, patient_id, note_id, category, kind, "
            + "description, created_by, updated_by) VALUES (" + org + ", " + patient + ", " + note
            + ", 'MEDICACION', 'ALTA', 'Ibuprofeno', " + someUser + ", " + someUser + ")";
    }

    private String correctionInsert(long org, long note) {
        return "INSERT INTO consultation_note_correction (organization_id, note_id, field, corrected_text, reason, "
            + "created_by, updated_by) VALUES (" + org + ", " + note + ", 'REASON', 'Dolor', 'Error', " + someUser + ", " + someUser + ")";
    }

    private long seedSealedNote(Statement s, long org, long patient) throws SQLException {
        long note = id(s, noteInsert(org, patient) + " RETURNING id");
        s.execute(diagnosisInsert(org, note));
        s.execute(entryInsert(org, patient, note));
        s.execute("UPDATE consultation_note SET reason = 'Dolr', status = 'SELLADA', sealed_at = now(), sealed_by = "
            + someUser + " WHERE id = " + note);
        s.execute(correctionInsert(org, note));
        return note;
    }

    private void asClinic(long organizationId) {
        IdentityContext.set(someUser);
        OrganizationContext.set(organizationId);
    }

    private void assertOnlyOwnRows(JpaRepository<?, Long> repository) {
        asClinic(orgA);
        assertThat(repository.findAll()).hasSize(1);
        asClinic(orgB);
        assertThat(repository.findAll()).hasSize(1);
        IdentityContext.clear();
        OrganizationContext.clear();
        assertThatThrownBy(repository::findAll)
            .as("sin contexto la consulta debe FALLAR, no devolver vacío")
            .isInstanceOf(Exception.class);
    }

    @Test
    void notes_are_isolated_per_clinic() {
        assertOnlyOwnRows(noteRepository);
        asClinic(orgB);
        assertThat(noteRepository.findById(noteA)).isEmpty();
    }

    @Test
    void note_diagnoses_are_isolated_per_clinic() {
        assertOnlyOwnRows(diagnosisRepository);
    }

    @Test
    void corrections_are_isolated_per_clinic() {
        assertOnlyOwnRows(correctionRepository);
    }

    @Test
    void background_entries_are_isolated_per_clinic() {
        assertOnlyOwnRows(entryRepository);
    }

    // --- FK compuestas por tenant: declarar la organización A y apuntar a un padre de B ---

    private static void assertCompositeFkViolation(Throwable e, String constraint) {
        assertThat(e).isInstanceOf(SQLException.class);
        assertThat(((SQLException) e).getSQLState()).as("foreign_key_violation").isEqualTo("23503");
        assertThat(e.getMessage()).contains(constraint);
    }

    @Test
    void a_note_cannot_point_to_a_patient_of_another_clinic() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            assertThatThrownBy(() -> s.execute(noteInsert(orgA, patientB)))
                .satisfies(e -> assertCompositeFkViolation(e, "fk_consultation_note_patient"));
            s.execute(noteInsert(orgB, patientB)); // control
        }
    }

    @Test
    void children_cannot_point_to_a_note_of_another_clinic() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            long openB = id(s, noteInsert(orgB, patientB) + " RETURNING id");
            s.execute(diagnosisInsert(orgB, openB)); // control
            assertThatThrownBy(() -> s.execute(diagnosisInsert(orgA, openB)))
                .satisfies(e -> assertCompositeFkViolation(e, "fk_consultation_note_diagnosis_note"));
            assertThatThrownBy(() -> s.execute(entryInsert(orgA, patientA, openB)))
                .satisfies(e -> assertCompositeFkViolation(e, "fk_clinical_background_entry_note"));
            assertThatThrownBy(() -> s.execute(correctionInsert(orgA, noteB)))
                .satisfies(e -> assertCompositeFkViolation(e, "fk_consultation_note_correction_note"));
        }
    }

    @Test
    void a_closing_entry_cannot_point_to_an_entry_of_another_clinic() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            long openA = id(s, noteInsert(orgA, patientA) + " RETURNING id");
            assertThatThrownBy(() -> s.execute("INSERT INTO clinical_background_entry (organization_id, patient_id, "
                + "note_id, category, kind, closes_entry_id, created_by, updated_by) VALUES (" + orgA + ", " + patientA
                + ", " + openA + ", 'MEDICACION', 'BAJA', " + entryB + ", " + someUser + ", " + someUser + ")"))
                .satisfies(e -> assertCompositeFkViolation(e, "fk_clinical_background_entry_closes"));
        }
    }
}
