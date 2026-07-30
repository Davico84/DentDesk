package com.odontorisas.clinicalhistory;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.DiagnosisLocation;
import com.odontorisas.common.HistoriaClinicaStatus;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.persistence.entity.HistoriaClinica;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.HistoriaClinicaRepository;
import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.service.clinicalhistory.Cie10CodeNotFoundException;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryDiagnosisService;
import com.odontorisas.service.clinicalhistory.DiagnosisData;
import com.odontorisas.service.clinicalhistory.DiagnosisView;
import com.odontorisas.service.clinicalhistory.HistoriaClinicaSealedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Diagnósticos CIE10 de la Historia Clínica (design.md D5/D11, spec: diagnóstico CIE10). Contra
 * PostgreSQL real: valida existencia del código contra el catálogo cargado de verdad.
 */
@SpringBootTest
class ClinicalHistoryDiagnosisServiceIT extends AbstractTenantAwareIntegrationTest {

    private long ORG;
    private long SOME_USER;

    @Autowired
    private ClinicalHistoryDiagnosisService diagnosisService;
    @Autowired
    private com.odontorisas.service.clinicalhistory.ClinicalHistoryService clinicalHistoryService;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private HistoriaClinicaRepository historiaClinicaRepository;
    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void seed() throws SQLException {
        transactionTemplate = new TransactionTemplate(platformTransactionManager);
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("TRUNCATE clinical_history_diagnosis, historia_clinica, patients RESTART IDENTITY CASCADE");
            // Id autogenerado (RETURNING), no fijo: un id hardcodeado desincroniza la secuencia de
            // Postgres del contenido real de la tabla y choca con inserciones normales posteriores
            // en la misma suite (hallazgo real: colisión con AuthService.register() en CI).
            String suffix = java.util.UUID.randomUUID().toString();
            try (var rs = s.executeQuery("INSERT INTO organizations (name, slug) VALUES "
                + "('Clinica Diag Service', 'clinica-diag-service-" + suffix + "') RETURNING id")) {
                rs.next();
                ORG = rs.getLong(1);
            }
            try (var rs = s.executeQuery("INSERT INTO users (email, password_hash) VALUES "
                + "('diag-service-" + suffix + "@test.com', 'x') RETURNING id")) {
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

    private Long aPatientWithDraftHistoria(String name) {
        Long patientId = patientRepository.save(Patient.builder()
            .fullName(name)
            .birthDate(LocalDate.of(1990, 1, 1))
            .status(PatientStatus.ACTIVE)
            .allergies(List.of())
            .conditions(List.of())
            .currentMedication(List.of())
            .build()).getId();
        clinicalHistoryService.create(patientId);
        return patientId;
    }

    private void sealDirectlyInDb(Long patientId) {
        transactionTemplate.executeWithoutResult(status ->
            historiaClinicaRepository.findByPatientId(patientId).ifPresent(h -> {
                h.setStatus(HistoriaClinicaStatus.COMPLETED);
                historiaClinicaRepository.save(h);
            }));
    }

    @Test
    void adds_a_general_diagnosis_with_authorship_captured() {
        Long patientId = aPatientWithDraftHistoria("General");

        DiagnosisView view = diagnosisService.add(patientId,
            new DiagnosisData("A00", DiagnosisLocation.GENERAL, null, TipoDiagnostico.PRESUNTIVO));

        assertThat(view.cie10Code()).isEqualTo("A00");
        assertThat(view.ubicacion()).isEqualTo(DiagnosisLocation.GENERAL);
        assertThat(view.createdAt()).isNotNull();
        assertThat(view.createdBy()).isEqualTo(SOME_USER);
    }

    @Test
    void adds_a_piece_diagnosis_with_a_valid_fdi() {
        Long patientId = aPatientWithDraftHistoria("Pieza");

        DiagnosisView view = diagnosisService.add(patientId,
            new DiagnosisData("A00", DiagnosisLocation.PIEZA, 36, TipoDiagnostico.DEFINITIVO));

        assertThat(view.fdiNumber()).isEqualTo(36);
    }

    @Test
    void rejects_a_nonexistent_cie10_code() {
        Long patientId = aPatientWithDraftHistoria("Codigo Invalido");

        assertThatThrownBy(() -> diagnosisService.add(patientId,
            new DiagnosisData("ZZZZ", DiagnosisLocation.GENERAL, null, TipoDiagnostico.PRESUNTIVO)))
            .isInstanceOf(Cie10CodeNotFoundException.class);
    }

    @Test
    void rejects_changing_a_diagnosis_once_the_history_is_completed() {
        Long patientId = aPatientWithDraftHistoria("Sellada Diag");
        DiagnosisView added = diagnosisService.add(patientId,
            new DiagnosisData("A00", DiagnosisLocation.GENERAL, null, TipoDiagnostico.PRESUNTIVO));
        sealDirectlyInDb(patientId);

        assertThatThrownBy(() -> diagnosisService.add(patientId,
            new DiagnosisData("A01", DiagnosisLocation.GENERAL, null, TipoDiagnostico.PRESUNTIVO)))
            .isInstanceOf(HistoriaClinicaSealedException.class);
        assertThatThrownBy(() -> diagnosisService.remove(patientId, added.id()))
            .isInstanceOf(HistoriaClinicaSealedException.class);
    }

    @Test
    void removes_a_diagnosis_while_in_draft() {
        Long patientId = aPatientWithDraftHistoria("Elimina");
        DiagnosisView added = diagnosisService.add(patientId,
            new DiagnosisData("A00", DiagnosisLocation.GENERAL, null, TipoDiagnostico.PRESUNTIVO));

        diagnosisService.remove(patientId, added.id());

        assertThat(diagnosisService.listByPatient(patientId)).isEmpty();
    }
}
