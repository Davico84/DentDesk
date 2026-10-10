package com.odontorisas.clinicalhistory;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.HistoriaClinicaStatus;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.HistoriaClinicaRepository;
import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryData;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryService;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryView;
import com.odontorisas.service.clinicalhistory.HistoriaClinicaAlreadyExistsException;
import com.odontorisas.service.clinicalhistory.HistoriaClinicaSealedException;
import com.odontorisas.service.patient.PatientNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Servicio de dominio de la Historia Clínica: creación en {@code DRAFT}, guardado incremental y
 * el sellado de escritura tras {@code COMPLETED} (design.md D1/D2/D3/D9, spec: única por
 * paciente/clínica, guardado incremental, sellada tras COMPLETED). Contra PostgreSQL real —
 * la unicidad concurrente (spec: creación concurrente) necesita el motor, no un mock.
 */
@SpringBootTest
class ClinicalHistoryServiceIT extends AbstractTenantAwareIntegrationTest {

    private long ORG;
    private long SOME_USER;

    @Autowired
    private ClinicalHistoryService clinicalHistoryService;
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
            // Id autogenerado (RETURNING), no fijo: ver ClinicalHistoryDiagnosisServiceIT.
            String suffix = java.util.UUID.randomUUID().toString();
            try (var rs = s.executeQuery("INSERT INTO organizations (name, slug) VALUES "
                + "('Clinica CH Service', 'clinica-ch-service-" + suffix + "') RETURNING id")) {
                rs.next();
                ORG = rs.getLong(1);
            }
            try (var rs = s.executeQuery("INSERT INTO users (email, password_hash) VALUES "
                + "('ch-service-" + suffix + "@test.com', 'x') RETURNING id")) {
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

    private ClinicalHistoryData emptyData() {
        return new ClinicalHistoryData(null, null, null, null, null, null,
            false, null, false, null, false, null);
    }

    @Test
    void creates_the_first_clinical_history_of_a_patient_in_draft() {
        Long patientId = aPatient("Ada");

        ClinicalHistoryView view = clinicalHistoryService.create(patientId);

        assertThat(view.status()).isEqualTo(HistoriaClinicaStatus.DRAFT);
        assertThat(view.patientId()).isEqualTo(patientId);
    }

    @Test
    void rejects_a_second_clinical_history_for_the_same_patient() {
        Long patientId = aPatient("Ada");
        clinicalHistoryService.create(patientId);

        assertThatThrownBy(() -> clinicalHistoryService.create(patientId))
            .isInstanceOf(HistoriaClinicaAlreadyExistsException.class);
        assertThat(historiaClinicaRepository.findAll()).hasSize(1);
    }

    @Test
    void rejects_creation_for_a_nonexistent_patient() {
        assertThatThrownBy(() -> clinicalHistoryService.create(999L))
            .isInstanceOf(PatientNotFoundException.class);
    }

    @Test
    void two_concurrent_creations_for_the_same_patient_only_one_succeeds() throws InterruptedException {
        Long patientId = aPatient("Concurrente");
        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try {
            for (int i = 0; i < attempts; i++) {
                pool.submit(() -> {
                    IdentityContext.set(SOME_USER);
                    OrganizationContext.set(ORG);
                    ready.countDown();
                    try {
                        start.await();
                        clinicalHistoryService.create(patientId);
                        succeeded.incrementAndGet();
                    } catch (HistoriaClinicaAlreadyExistsException e) {
                        rejected.incrementAndGet();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        IdentityContext.clear();
                        OrganizationContext.clear();
                    }
                });
            }
            ready.await();
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(attempts - 1);
        assertThat(historiaClinicaRepository.findAll()).hasSize(1);
    }

    @Test
    void saves_a_draft_with_incomplete_sections() {
        Long patientId = aPatient("Incompleta");
        clinicalHistoryService.create(patientId);

        ClinicalHistoryView updated = clinicalHistoryService.update(patientId,
            new ClinicalHistoryData("Dolor de muela", null, null, null, null, null,
                false, null, false, null, false, null));

        assertThat(updated.status()).isEqualTo(HistoriaClinicaStatus.DRAFT);
        assertThat(updated.motivoConsulta()).isEqualTo("Dolor de muela");
        assertThat(updated.antecedentesPersonales()).isNull();
    }

    @Test
    void reopening_and_editing_an_existing_draft_saves_the_changes() {
        Long patientId = aPatient("Retoma");
        clinicalHistoryService.create(patientId);
        clinicalHistoryService.update(patientId,
            new ClinicalHistoryData("Motivo 1", null, null, null, null, null,
                false, null, false, null, false, null));

        ClinicalHistoryView second = clinicalHistoryService.update(patientId,
            new ClinicalHistoryData("Motivo 1", "Enfermedad actual añadida", null, null, null, null,
                true, null, false, null, false, null));

        assertThat(second.enfermedadActual()).isEqualTo("Enfermedad actual añadida");
        assertThat(second.alergiasRevisado()).isTrue();
    }

    @Test
    void the_clinical_history_does_not_touch_the_patients_clinical_data() {
        Long patientId = aPatient("Con Anamnesis Previa");
        Patient before = patientRepository.findById(patientId).orElseThrow();
        assertThat(before.getAllergies()).isEmpty();

        clinicalHistoryService.create(patientId);
        clinicalHistoryService.update(patientId,
            new ClinicalHistoryData(null, null, null, null, null, null,
                true, "penicilina", false, null, false, null));

        // La Historia guardó su propia anamnesis; patients no fue leído ni modificado (D9).
        Patient after = patientRepository.findById(patientId).orElseThrow();
        assertThat(after.getAllergies()).isEmpty();
        assertThat(after.getConditions()).isEmpty();
        assertThat(after.getCurrentMedication()).isEmpty();
    }

    @Test
    void rejects_direct_edition_of_a_sealed_clinical_history() {
        Long patientId = aPatient("Sellada");
        clinicalHistoryService.create(patientId);
        sealDirectlyInDb(patientId);

        assertThatThrownBy(() -> clinicalHistoryService.update(patientId, emptyData()))
            .isInstanceOf(HistoriaClinicaSealedException.class);
    }

    /**
     * Sella por SQL directo: el sellado real (grupo 5) aún no existe en este punto de la build.
     * Usa {@link TransactionTemplate} sobre el {@code PlatformTransactionManager} real —igual que
     * el proxy de un {@code @Service}— para que {@code TenantAwareTransactionManager.doBegin}
     * fije el contexto: un método propio de esta clase de test no está proxied por Spring (no es
     * un bean), así que un {@code @Transactional} aquí no tiene ningún efecto.
     */
    void sealDirectlyInDb(Long patientId) {
        transactionTemplate.executeWithoutResult(status ->
            historiaClinicaRepository.findByPatientId(patientId).ifPresent(h -> {
                h.setStatus(HistoriaClinicaStatus.COMPLETED);
                historiaClinicaRepository.save(h);
            }));
    }
}
