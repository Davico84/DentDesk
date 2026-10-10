package com.odontorisas.clinicalhistory;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.DiagnosisLocation;
import com.odontorisas.common.HistoriaClinicaStatus;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.service.clinicalhistory.ChecklistIncompleteException;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryData;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryDiagnosisService;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryService;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryView;
import com.odontorisas.service.clinicalhistory.DiagnosisData;
import com.odontorisas.service.clinicalhistory.EnablementView;
import com.odontorisas.service.clinicalhistory.HistoriaClinicaSealedException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sellado a {@code COMPLETED} y estado de habilitación (design.md D4/D8, spec: sellado con
 * checklist, estado consultable). Contra PostgreSQL real.
 */
@SpringBootTest
class ClinicalHistoryCompletionServiceIT extends AbstractTenantAwareIntegrationTest {

    private long ORG;
    private long SOME_USER;

    @Autowired
    private ClinicalHistoryService clinicalHistoryService;
    @Autowired
    private ClinicalHistoryDiagnosisService diagnosisService;
    @Autowired
    private PatientRepository patientRepository;

    @BeforeEach
    void seed() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("TRUNCATE clinical_history_diagnosis, historia_clinica, patients RESTART IDENTITY CASCADE");
            // Id autogenerado (RETURNING), no fijo: ver ClinicalHistoryDiagnosisServiceIT.
            String suffix = java.util.UUID.randomUUID().toString();
            try (var rs = s.executeQuery("INSERT INTO organizations (name, slug) VALUES "
                + "('Clinica Completion', 'clinica-completion-" + suffix + "') RETURNING id")) {
                rs.next();
                ORG = rs.getLong(1);
            }
            try (var rs = s.executeQuery("INSERT INTO users (email, password_hash) VALUES "
                + "('completion-" + suffix + "@test.com', 'x') RETURNING id")) {
                rs.next();
                SOME_USER = rs.getLong(1);
            }
        }
        IdentityContext.set(SOME_USER);
        OrganizationContext.set(ORG);
    }

    @AfterEach
    void clearContext() {
        IdentityContext.clear();
        OrganizationContext.clear();
    }

    private Long aPatient(String name) {
        return patientRepository.save(Patient.builder()
            .fullName(name)
            .birthDate(LocalDate.of(1990, 1, 1))
            .status(PatientStatus.ACTIVE)
            .allergies(List.of())
            .conditions(List.of())
            .currentMedication(List.of())
            .build()).getId();
    }

    private ClinicalHistoryData fullData() {
        return new ClinicalHistoryData(
            "Dolor de muela", "Desde hace 3 días", "Ninguno relevante", "Sin hallazgos anormales",
            "Favorable", null,
            true, null, true, null, true, null);
    }

    private Long aPatientReadyToComplete(String name) {
        Long patientId = aPatient(name);
        clinicalHistoryService.create(patientId);
        clinicalHistoryService.update(patientId, fullData());
        diagnosisService.add(patientId,
            new DiagnosisData("A00", DiagnosisLocation.GENERAL, null, TipoDiagnostico.PRESUNTIVO));
        return patientId;
    }

    @Test
    void completes_when_the_checklist_is_satisfied() {
        Long patientId = aPatientReadyToComplete("Completa");

        ClinicalHistoryView view = clinicalHistoryService.complete(patientId, true);

        assertThat(view.status()).isEqualTo(HistoriaClinicaStatus.COMPLETED);
    }

    @Test
    void rejects_completion_with_a_missing_critical_item() {
        Long patientId = aPatient("Incompleta");
        clinicalHistoryService.create(patientId);
        clinicalHistoryService.update(patientId, fullData());
        // Sin diagnóstico: falta un ítem del checklist.

        assertThatThrownBy(() -> clinicalHistoryService.complete(patientId, true))
            .isInstanceOf(ChecklistIncompleteException.class)
            .satisfies(e -> assertThat(((ChecklistIncompleteException) e).getMissingFields())
                .contains("diagnosticos"));
    }

    @Test
    void rejects_completion_without_an_initial_dental_chart() {
        // El sellado coordinado real vive en CloseAdmissionServiceIT; esto prueba SOLO que este
        // servicio, llamado con hasInitialDentalChart=false, reporta el ítem que le corresponde
        // (design.md D4, ADR-0012 D5, add-dental-chart: la Historia ya no se sella sola).
        Long patientId = aPatientReadyToComplete("Sin odontograma");

        assertThatThrownBy(() -> clinicalHistoryService.complete(patientId, false))
            .isInstanceOf(ChecklistIncompleteException.class)
            .satisfies(e -> assertThat(((ChecklistIncompleteException) e).getMissingFields())
                .containsExactly("dentalChart"));
    }

    @Test
    void an_empty_alergias_field_does_not_count_as_reviewed() {
        Long patientId = aPatient("Alergias Vacias");
        clinicalHistoryService.create(patientId);
        clinicalHistoryService.update(patientId, new ClinicalHistoryData(
            "Motivo", "Antecedentes", "Examen", "Pronostico", null, null,
            false, null, true, null, true, null));
        diagnosisService.add(patientId,
            new DiagnosisData("A00", DiagnosisLocation.GENERAL, null, TipoDiagnostico.PRESUNTIVO));

        assertThatThrownBy(() -> clinicalHistoryService.complete(patientId, true))
            .isInstanceOf(ChecklistIncompleteException.class)
            .satisfies(e -> assertThat(((ChecklistIncompleteException) e).getMissingFields())
                .contains("alergiasRevisado"));
    }

    @Test
    void an_empty_medicacion_field_does_not_count_as_no_medication() {
        Long patientId = aPatient("Medicacion Vacia");
        clinicalHistoryService.create(patientId);
        clinicalHistoryService.update(patientId, new ClinicalHistoryData(
            "Motivo", "Antecedentes", "Examen", "Pronostico", null, null,
            true, null, false, null, true, null));
        diagnosisService.add(patientId,
            new DiagnosisData("A00", DiagnosisLocation.GENERAL, null, TipoDiagnostico.PRESUNTIVO));

        assertThatThrownBy(() -> clinicalHistoryService.complete(patientId, true))
            .isInstanceOf(ChecklistIncompleteException.class)
            .satisfies(e -> assertThat(((ChecklistIncompleteException) e).getMissingFields())
                .contains("medicacionRevisado"));
    }

    @Test
    void rejects_completing_an_already_completed_history() {
        Long patientId = aPatientReadyToComplete("Doble Sello");
        clinicalHistoryService.complete(patientId, true);

        assertThatThrownBy(() -> clinicalHistoryService.complete(patientId, true))
            .isInstanceOf(HistoriaClinicaSealedException.class);
    }

    @Test
    void a_patient_with_a_completed_history_is_enabled_for_treatment() {
        Long patientId = aPatientReadyToComplete("Habilitado");
        clinicalHistoryService.complete(patientId, true);

        EnablementView enablement = clinicalHistoryService.getEnablementStatus(patientId);

        assertThat(enablement.status()).isEqualTo("COMPLETED");
        assertThat(enablement.enabledForTreatment()).isTrue();
    }

    @Test
    void a_patient_with_only_a_draft_is_not_enabled() {
        Long patientId = aPatient("Solo Draft");
        clinicalHistoryService.create(patientId);

        EnablementView enablement = clinicalHistoryService.getEnablementStatus(patientId);

        assertThat(enablement.status()).isEqualTo("DRAFT");
        assertThat(enablement.enabledForTreatment()).isFalse();
    }

    @Test
    void a_patient_without_a_clinical_history_is_not_enabled() {
        Long patientId = aPatient("Sin Historia");

        EnablementView enablement = clinicalHistoryService.getEnablementStatus(patientId);

        assertThat(enablement.status()).isEqualTo("NONE");
        assertThat(enablement.enabledForTreatment()).isFalse();
    }
}
