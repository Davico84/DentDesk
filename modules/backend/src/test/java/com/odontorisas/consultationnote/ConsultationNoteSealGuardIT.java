package com.odontorisas.consultationnote;

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
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El sellado de la nota se protege también en la persistencia (`update-clinical-history` design.md
 * D2/D6/D7, migración V22). Todo por SQL crudo con la conexión owner, que no pasa por ningún
 * servicio: ahí la única barrera son los triggers. Cubre la parte de datos del scenario
 * <i>Rechazar cambios a una nota sellada aun por API directa</i>; la ruta por servicio la cubre
 * {@code ConsultationNoteServiceIT}.
 */
@SpringBootTest
class ConsultationNoteSealGuardIT extends AbstractTenantAwareIntegrationTest {

    private long org;
    private long someUser;
    private long patientId;
    private long sealedNote;
    private long openNote;
    private long sealedDiagnosis;
    private long sealedEntry;
    private long correction;

    @Autowired
    private PatientRepository patientRepository;

    @BeforeEach
    void aSealedNoteWithChildrenAndAnOpenOne() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("TRUNCATE consultation_note_correction, clinical_background_entry, consultation_note_diagnosis, "
                + "consultation_note, patients RESTART IDENTITY CASCADE");
            String suffix = UUID.randomUUID().toString();
            org = insertReturningId(s, "INSERT INTO organizations (name, slug) VALUES "
                + "('Clinica Notas', 'clinica-notas-" + suffix + "') RETURNING id");
            someUser = insertReturningId(s, "INSERT INTO users (email, password_hash) VALUES "
                + "('notas-" + suffix + "@test.local', 'x') RETURNING id");
        }
        IdentityContext.set(someUser);
        OrganizationContext.set(org);
        patientId = patientRepository.save(Patient.builder()
            .fullName("Paciente con notas")
            .birthDate(LocalDate.of(1980, 1, 1))
            .status(PatientStatus.ACTIVE)
            .allergies(List.of())
            .conditions(List.of())
            .currentMedication(List.of())
            .build()).getId();

        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            // Se inserta ABIERTA y se sella con un UPDATE: es la única transición que el trigger admite.
            sealedNote = insertReturningId(s, "INSERT INTO consultation_note (organization_id, patient_id, attended_at, "
                + "reason, examination, treatment, created_by, updated_by) VALUES (" + org + ", " + patientId
                + ", now() - interval '2 days', 'Dolor', 'Caries 36', 'Restauración', " + someUser + ", " + someUser
                + ") RETURNING id");
            sealedDiagnosis = insertReturningId(s, diagnosisInsert(sealedNote) + " RETURNING id");
            sealedEntry = insertReturningId(s, entryInsert(sealedNote, "'ALTA'", "'Penicilina'", "NULL") + " RETURNING id");
            s.execute("UPDATE consultation_note SET status = 'SELLADA', sealed_at = now(), sealed_by = " + someUser
                + ", signer_full_name = 'Dra. Prueba', signer_cop_number = '12345', signer_licensed_until = current_date"
                + " WHERE id = " + sealedNote);
            correction = insertReturningId(s, correctionInsert(sealedNote) + " RETURNING id");
            openNote = insertReturningId(s, "INSERT INTO consultation_note (organization_id, patient_id, attended_at, "
                + "created_by, updated_by) VALUES (" + org + ", " + patientId + ", now(), " + someUser + ", "
                + someUser + ") RETURNING id");
        }
    }

    @AfterEach
    void clearContext() {
        IdentityContext.clear();
        OrganizationContext.clear();
    }

    private static long insertReturningId(Statement s, String sql) throws SQLException {
        try (var rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String diagnosisInsert(long noteId) {
        return "INSERT INTO consultation_note_diagnosis (organization_id, note_id, cie10_code, ubicacion, "
            + "tipo_diagnostico, created_by, updated_by) SELECT " + org + ", " + noteId + ", code, 'GENERAL', "
            + "'DEFINITIVO', " + someUser + ", " + someUser + " FROM cie10_code LIMIT 1";
    }

    private String entryInsert(long noteId, String kind, String description, String closes) {
        return "INSERT INTO clinical_background_entry (organization_id, patient_id, note_id, category, kind, "
            + "description, closes_entry_id, created_by, updated_by) VALUES (" + org + ", " + patientId + ", "
            + noteId + ", 'ALERGIA_RAM', " + kind + ", " + description + ", " + closes + ", " + someUser + ", "
            + someUser + ")";
    }

    private String correctionInsert(long noteId) {
        return "INSERT INTO consultation_note_correction (organization_id, note_id, field, corrected_text, reason, "
            + "created_by, updated_by) VALUES (" + org + ", " + noteId + ", 'EXAMINATION', 'Caries 37', 'Pieza mal anotada', "
            + someUser + ", " + someUser + ")";
    }

    private void assertRejectedAsSealed(String sql) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            assertThatThrownBy(() -> s.execute(sql))
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState())
                    .as("object_not_in_prerequisite_state, del trigger de V22").isEqualTo("55000"));
        }
    }

    @Test
    void a_sealed_note_cannot_be_reopened_rewritten_or_deleted() throws SQLException {
        assertRejectedAsSealed("UPDATE consultation_note SET status = 'ABIERTA', sealed_at = NULL, sealed_by = NULL "
            + "WHERE id = " + sealedNote);
        assertRejectedAsSealed("UPDATE consultation_note SET examination = 'otro' WHERE id = " + sealedNote);
        assertRejectedAsSealed("UPDATE consultation_note SET sealed_at = now() WHERE id = " + sealedNote);
        assertRejectedAsSealed("DELETE FROM consultation_note WHERE id = " + sealedNote);
    }

    @Test
    void diagnoses_of_a_sealed_note_cannot_be_added_changed_or_removed() throws SQLException {
        assertRejectedAsSealed(diagnosisInsert(sealedNote));
        assertRejectedAsSealed("UPDATE consultation_note_diagnosis SET tipo_diagnostico = 'PRESUNTIVO' WHERE id = "
            + sealedDiagnosis);
        assertRejectedAsSealed("DELETE FROM consultation_note_diagnosis WHERE id = " + sealedDiagnosis);
        // Ni mudarlo a una nota abierta: el destino no importa si el origen está sellado.
        assertRejectedAsSealed("UPDATE consultation_note_diagnosis SET note_id = " + openNote + " WHERE id = "
            + sealedDiagnosis);
    }

    @Test
    void background_entries_of_a_sealed_note_cannot_be_added_changed_or_removed() throws SQLException {
        assertRejectedAsSealed(entryInsert(sealedNote, "'ALTA'", "'Látex'", "NULL"));
        assertRejectedAsSealed("UPDATE clinical_background_entry SET description = 'otra' WHERE id = " + sealedEntry);
        assertRejectedAsSealed("DELETE FROM clinical_background_entry WHERE id = " + sealedEntry);
    }

    @Test
    void a_correction_is_never_updated_nor_deleted() throws SQLException {
        assertRejectedAsSealed("UPDATE consultation_note_correction SET corrected_text = 'otra' WHERE id = " + correction);
        assertRejectedAsSealed("DELETE FROM consultation_note_correction WHERE id = " + correction);
    }

    @Test
    void a_correction_cannot_target_an_open_note() throws SQLException {
        assertRejectedAsSealed(correctionInsert(openNote));
    }

    @Test
    void the_open_note_and_its_children_stay_writable() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute(diagnosisInsert(openNote));
            s.execute(entryInsert(openNote, "'BAJA'", "NULL", String.valueOf(sealedEntry)));
            s.execute("UPDATE consultation_note SET reason = 'Control' WHERE id = " + openNote);
            s.execute("UPDATE consultation_note SET status = 'SELLADA', sealed_at = now(), sealed_by = " + someUser
                + ", signer_full_name = 'Dra. Prueba', signer_cop_number = '12345', signer_licensed_until = current_date"
                + " WHERE id = " + openNote);
        }
    }

    @Test
    void an_entry_is_closed_at_most_once() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute(entryInsert(openNote, "'BAJA'", "NULL", String.valueOf(sealedEntry)));
            assertThatThrownBy(() -> s.execute(entryInsert(openNote, "'BAJA'", "NULL", String.valueOf(sealedEntry))))
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).as("unique_violation").isEqualTo("23505"));
        }
    }
}
