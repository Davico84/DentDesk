package com.odontorisas.clinicalhistory;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.DiagnosisLocation;
import com.odontorisas.common.HistoriaClinicaStatus;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.persistence.entity.ClinicalHistoryDiagnosis;
import com.odontorisas.persistence.entity.HistoriaClinica;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.ClinicalHistoryDiagnosisRepository;
import com.odontorisas.persistence.repository.HistoriaClinicaRepository;
import com.odontorisas.persistence.repository.PatientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de aislamiento de <b>MOTOR</b> (RLS) para {@code historia_clinica} y
 * {@code clinical_history_diagnosis} (design.md D2b, hallazgo de review): son entidades de
 * negocio con RLS <b>propia</b>, no heredada de {@code patients} por la FK. Mismo patrón que
 * {@code PatientRlsIT} — rol de aplicación no-superusuario, contexto poblado como lo haría el
 * filtro JWT, sin llamar nunca a {@code TenantSession.bind(...)}.
 */
@SpringBootTest
class ClinicalHistoryRlsIT extends AbstractTenantAwareIntegrationTest {

    private long ORG_A;
    private long ORG_B;
    private long SOME_USER;

    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private HistoriaClinicaRepository historiaClinicaRepository;
    @Autowired
    private ClinicalHistoryDiagnosisRepository diagnosisRepository;

    @BeforeEach
    void seedOrganizationsAndUsers() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("TRUNCATE clinical_history_diagnosis, historia_clinica, patients RESTART IDENTITY CASCADE");
            // Id autogenerado (RETURNING), no fijo: ver ClinicalHistoryDiagnosisServiceIT.
            String suffix = java.util.UUID.randomUUID().toString();
            try (var rs = s.executeQuery("INSERT INTO organizations (name, slug) VALUES "
                + "('Clinica A', 'clinica-a-ch-" + suffix + "') RETURNING id")) {
                rs.next();
                ORG_A = rs.getLong(1);
            }
            try (var rs = s.executeQuery("INSERT INTO organizations (name, slug) VALUES "
                + "('Clinica B', 'clinica-b-ch-" + suffix + "') RETURNING id")) {
                rs.next();
                ORG_B = rs.getLong(1);
            }
            try (var rs = s.executeQuery("INSERT INTO users (email, password_hash) VALUES "
                + "('someone-" + suffix + "@ch.test', 'x') RETURNING id")) {
                rs.next();
                SOME_USER = rs.getLong(1);
            }
        }
    }

    @AfterEach
    void clearContext() {
        IdentityContext.clear();
        OrganizationContext.clear();
    }

    private void asClinic(long organizationId) {
        IdentityContext.set(SOME_USER);
        OrganizationContext.set(organizationId);
    }

    @Transactional
    Long createPatientAndHistoria(String fullName) {
        Patient patient = patientRepository.save(Patient.builder()
            .fullName(fullName)
            .birthDate(java.time.LocalDate.of(1990, 1, 1))
            .status(PatientStatus.ACTIVE)
            .allergies(java.util.List.of())
            .conditions(java.util.List.of())
            .currentMedication(java.util.List.of())
            .build());
        HistoriaClinica historia = historiaClinicaRepository.save(HistoriaClinica.builder()
            .patientId(patient.getId())
            .status(HistoriaClinicaStatus.DRAFT)
            .alergiasRevisado(false)
            .medicacionRevisado(false)
            .antecedentesFamiliaresRevisado(false)
            .build());
        return historia.getId();
    }

    @Test
    void a_clinic_only_sees_its_own_historia_clinica() {
        asClinic(ORG_A);
        Long historiaA = createPatientAndHistoria("Paciente de A");

        asClinic(ORG_B);
        createPatientAndHistoria("Paciente de B");

        assertThat(historiaClinicaRepository.findAll()).hasSize(1);

        asClinic(ORG_A);
        assertThat(historiaClinicaRepository.findAll()).hasSize(1);
        assertThat(historiaClinicaRepository.findById(historiaA)).isPresent();
    }

    @Test
    void a_clinic_cannot_read_a_historia_clinica_of_another_clinic_even_by_id() {
        asClinic(ORG_A);
        Long historiaA = createPatientAndHistoria("Paciente de A");

        asClinic(ORG_B);
        assertThat(historiaClinicaRepository.findById(historiaA)).isEmpty();
    }

    @Test
    void an_operation_without_context_fails_closed() {
        assertThatThrownBy(() -> historiaClinicaRepository.findAll())
            .as("una consulta de historias clínicas sin contexto debe FALLAR, no devolver vacío")
            .isInstanceOf(Exception.class);
    }

    @Test
    void a_clinic_only_sees_its_own_diagnoses() {
        asClinic(ORG_A);
        Long historiaA = createPatientAndHistoria("Paciente Diag A");
        diagnosisRepository.save(ClinicalHistoryDiagnosis.builder()
            .historiaId(historiaA)
            .cie10Code("A00")
            .ubicacion(DiagnosisLocation.GENERAL)
            .tipoDiagnostico(TipoDiagnostico.PRESUNTIVO)
            .build());

        asClinic(ORG_B);
        assertThat(diagnosisRepository.findAll()).isEmpty();

        asClinic(ORG_A);
        assertThat(diagnosisRepository.findAll()).hasSize(1);
    }
}
