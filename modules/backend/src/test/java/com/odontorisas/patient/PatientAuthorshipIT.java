package com.odontorisas.patient;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.DocumentType;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.service.patient.PatientData;
import com.odontorisas.service.patient.PatientService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que {@code Patient} —la primera entidad que aplica el mecanismo de autoría de
 * {@code update-multi-tenancy}— registra {@code createdBy}/{@code updatedBy} automáticamente,
 * sin código propio (docs/backend.md §8.6, design.md de {@code update-patients}).
 */
@SpringBootTest
class PatientAuthorshipIT extends AbstractTenantAwareIntegrationTest {

    private static final long ORG_A = 1L;

    private long userA;
    private long userB;

    @Autowired
    private PatientService patientService;

    @Autowired
    private PatientRepository patientRepository;

    @BeforeEach
    void seedOrganizationAndUsers() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            // CASCADE: historia_clinica/clinical_history_diagnosis referencian patients por FK
            // (add-clinical-history); sin CASCADE, TRUNCATE falla aunque estén vacías.
            s.execute("TRUNCATE patients RESTART IDENTITY CASCADE");
            s.execute("INSERT INTO organizations (id, name, slug) VALUES "
                + "(" + ORG_A + ", 'Clinica A', 'clinica-a') ON CONFLICT (id) DO NOTHING");
            // created_by/updated_by tienen FK real a users(id) (design.md D1): hacen falta filas
            // reales. El contenedor es singleton (compartido por TODA la suite), así que un id
            // fijo choca con el IDENTITY de otra clase — se deja que la BD lo genere.
            userA = insertUser(s);
            userB = insertUser(s);
        }
    }

    private static long insertUser(Statement s) throws SQLException {
        try (ResultSet rs = s.executeQuery(
                "INSERT INTO users (email, password_hash) VALUES "
                + "('" + UUID.randomUUID() + "@test.local', 'x') RETURNING id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @AfterEach
    void clearContext() {
        IdentityContext.clear();
        OrganizationContext.clear();
    }

    private void asUser(long userId) {
        IdentityContext.set(userId);
        OrganizationContext.set(ORG_A);
    }

    private PatientData patient(String fullName, String document) {
        DocumentType type = document == null ? null : DocumentType.DNI;
        return new PatientData(fullName, type, document, LocalDate.of(2000, 1, 1), null,
            null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void records_who_created_and_who_last_modified() {
        asUser(userA);
        var created = patientService.create(patient("Ada", "AUTH-1"));

        Patient stored = patientRepository.findById(created.id()).orElseThrow();
        assertThat(stored.getCreatedBy()).isEqualTo(userA);
        assertThat(stored.getUpdatedBy()).isEqualTo(userA);

        IdentityContext.clear();
        asUser(userB);
        patientService.update(created.id(), patient("Ada Actualizada", "AUTH-1"));

        Patient updated = patientRepository.findById(created.id()).orElseThrow();
        assertThat(updated.getCreatedBy())
            .as("el autor de la creación no cambia con una edición posterior")
            .isEqualTo(userA);
        assertThat(updated.getUpdatedBy())
            .as("el autor de la última modificación sí cambia")
            .isEqualTo(userB);
    }

    @Test
    void a_patient_older_than_the_mechanism_keeps_authorship_unset() throws SQLException {
        // Simula un paciente creado ANTES de este change: insert directo, sin pasar por el
        // ciclo de vida JPA (que es lo único que dispara @CreatedBy/@LastModifiedBy).
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("INSERT INTO patients (organization_id, full_name, birth_date) VALUES "
                + "(" + ORG_A + ", 'Paciente Legado', '1990-01-01')");
        }

        asUser(userA);
        Patient legacy = patientRepository.findAll().get(0);

        assertThat(legacy.getCreatedBy()).isNull();
        assertThat(legacy.getUpdatedBy()).isNull();
    }
}
