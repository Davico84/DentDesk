package com.odontorisas.admission;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.DiagnosisLocation;
import com.odontorisas.common.HistoriaClinicaStatus;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.service.admission.AdmissionClosedResult;
import com.odontorisas.service.admission.CloseAdmissionService;
import com.odontorisas.service.clinicalhistory.ChecklistIncompleteException;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryData;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryDiagnosisService;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryService;
import com.odontorisas.service.clinicalhistory.DiagnosisData;
import com.odontorisas.service.clinicalhistory.HistoriaClinicaSealedException;
import com.odontorisas.service.dentalchart.DentalChartChecklistIncompleteException;
import com.odontorisas.service.dentalchart.DentalChartService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El sellado coordinado de la admisión (ADR-0012 D5, design.md D4, spec MODIFICADO de
 * `clinical-history`: <i>Sellado a COMPLETED con checklist mínimo afirmativo</i>). Contra
 * PostgreSQL real: el rollback ante un fallo del odontograma necesita el motor, no se puede
 * asumir por el orden del código (mismo criterio que la concurrencia real en otros ServiceIT).
 */
@SpringBootTest
class CloseAdmissionServiceIT extends AbstractTenantAwareIntegrationTest {

    private long org;
    private long someUser;

    @Autowired
    private CloseAdmissionService closeAdmissionService;
    @Autowired
    private ClinicalHistoryService clinicalHistoryService;
    @Autowired
    private ClinicalHistoryDiagnosisService diagnosisService;
    @Autowired
    private DentalChartService dentalChartService;
    @Autowired
    private PatientRepository patientRepository;

    @BeforeEach
    void seed() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("TRUNCATE dental_chart_finding_tooth, dental_chart_finding, dental_chart_block, "
                + "clinical_history_diagnosis, historia_clinica, patients RESTART IDENTITY CASCADE");
            String suffix = UUID.randomUUID().toString();
            try (var rs = s.executeQuery("INSERT INTO organizations (name, slug) VALUES "
                + "('Clinica Admision', 'clinica-admision-" + suffix + "') RETURNING id")) {
                rs.next();
                org = rs.getLong(1);
            }
            try (var rs = s.executeQuery("INSERT INTO users (email, password_hash) VALUES "
                + "('admision-" + suffix + "@test.local', 'x') RETURNING id")) {
                rs.next();
                someUser = rs.getLong(1);
            }
        }
        IdentityContext.set(someUser);
        OrganizationContext.set(org);
    }

    @AfterEach
    void clearContext() {
        IdentityContext.clear();
        OrganizationContext.clear();
    }

    @Transactional
    Long aPatient(String name) {
        return patientRepository.save(Patient.builder()
            .fullName(name)
            .birthDate(LocalDate.of(1990, 1, 1))
            .status(PatientStatus.ACTIVE)
            .allergies(List.of())
            .conditions(List.of())
            .currentMedication(List.of())
            .build()).getId();
    }

    private static final ClinicalHistoryData FULL_HC = new ClinicalHistoryData(
        "Dolor de muela", "Desde hace 3 días", "Ninguno relevante", "Sin hallazgos anormales",
        "Favorable", null, true, null, true, null, true, null);

    /** Historia con su checklist completo, sin tocar el odontograma. */
    private Long aPatientWithReadyClinicalHistory(String name) {
        Long patientId = aPatient(name);
        clinicalHistoryService.create(patientId);
        clinicalHistoryService.update(patientId, FULL_HC);
        diagnosisService.add(patientId,
            new DiagnosisData("A00", DiagnosisLocation.GENERAL, null, TipoDiagnostico.PRESUNTIVO));
        return patientId;
    }

    @Test
    void seals_both_documents_in_one_operation() {
        Long patientId = aPatientWithReadyClinicalHistory("Ambos");
        dentalChartService.createInitial(patientId, LocalDate.now());
        dentalChartService.setSpecifications(patientId, null);
        dentalChartService.setObservations(patientId, null);

        AdmissionClosedResult result = closeAdmissionService.close(patientId);

        assertThat(result.clinicalHistory().status()).isEqualTo(HistoriaClinicaStatus.COMPLETED);
        assertThat(result.dentalChart().status().name()).isEqualTo("SEALED");
    }

    @Test
    void rejects_closing_without_an_initial_dental_chart_reporting_what_is_missing() {
        Long patientId = aPatientWithReadyClinicalHistory("Sin odontograma");
        // Sin dentalChartService.createInitial(...): el paciente no tiene odontograma.

        assertThatThrownBy(() -> closeAdmissionService.close(patientId))
            .isInstanceOf(ChecklistIncompleteException.class)
            .satisfies(e -> assertThat(((ChecklistIncompleteException) e).getMissingFields())
                .contains("dentalChart"));
    }

    @Test
    void a_failure_sealing_the_dental_chart_leaves_the_clinical_history_in_draft() throws SQLException {
        Long patientId = aPatientWithReadyClinicalHistory("Rollback");
        // El bloque EXISTE (pasa el ítem "dentalChart" de la Historia) pero specs/obs NO están
        // revisadas: su propio checklist lo va a rechazar DESPUÉS de que la Historia ya se marcó
        // COMPLETED dentro de la misma transacción.
        dentalChartService.createInitial(patientId, LocalDate.now());

        assertThatThrownBy(() -> closeAdmissionService.close(patientId))
            .isInstanceOf(DentalChartChecklistIncompleteException.class);

        // SQL crudo con la conexión owner (bypass de JPA), no un repositorio autowireado: llamar
        // a un repositorio Spring Data justo después de una cadena @Transactional anidada que
        // lanzó (close -> complete -> sealInitial, las tres uniéndose a la misma transacción
        // física) deja la resolución de tenant de Hibernate en un estado que no vale la pena
        // depurar para una simple verificación de fixture — mismo criterio que sealBlock() en
        // DentalChartFindingServiceIT.
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             var rs = s.executeQuery("SELECT status FROM historia_clinica WHERE patient_id = " + patientId)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("status"))
                .as("un fallo al sellar el odontograma no debe dejar sellada la Historia")
                .isEqualTo("DRAFT");
        }
    }

    @Test
    void rejects_closing_an_already_closed_admission() {
        Long patientId = aPatientWithReadyClinicalHistory("Doble cierre");
        dentalChartService.createInitial(patientId, LocalDate.now());
        dentalChartService.setSpecifications(patientId, null);
        dentalChartService.setObservations(patientId, null);
        closeAdmissionService.close(patientId);

        assertThatThrownBy(() -> closeAdmissionService.close(patientId))
            .isInstanceOf(HistoriaClinicaSealedException.class);
    }

    @Test
    void a_clinical_history_sealed_before_this_rule_is_not_retroactively_affected() throws SQLException {
        // Simula una Historia COMPLETED de antes de add-dental-chart: sellada directo en la base,
        // sin pasar por el coordinador ni tener nunca un odontograma.
        Long patientId = aPatientWithReadyClinicalHistory("Historica");
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("UPDATE historia_clinica SET status = 'COMPLETED', completed_at = now() "
                + "WHERE patient_id = " + patientId);
        }

        var view = clinicalHistoryService.getByPatientId(patientId);

        assertThat(view.status()).isEqualTo(HistoriaClinicaStatus.COMPLETED);
    }
}
