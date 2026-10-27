package com.odontorisas.admission;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.ClinicalSignerFixtures;
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
import com.odontorisas.common.FindingCondition;
import com.odontorisas.common.FindingType;
import com.odontorisas.common.LocationKind;
import com.odontorisas.service.dentalchart.DentalChartBlockSealedException;
import com.odontorisas.service.dentalchart.DentalChartChecklistIncompleteException;
import com.odontorisas.service.dentalchart.DentalChartFindingData;
import com.odontorisas.service.dentalchart.DentalChartFindingService;
import com.odontorisas.service.dentalchart.DentalChartService;
import com.odontorisas.service.dentalchart.FindingLocationData;
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
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    @Autowired
    private DentalChartFindingService findingService;
    @Autowired
    private PlatformTransactionManager transactionManager;

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
            // Firmar exige capacidad clínica (update-staff-professional-credential): membresía y credencial vigentes.
            ClinicalSignerFixtures.enableSigner(owner, someUser, org);
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
            // Con la foto del firmante: desde V30 la base exige la foto en todo sellado nuevo (design.md D5).
            s.execute("UPDATE historia_clinica SET status = 'COMPLETED', completed_at = now(), completed_by = created_by"
                + ", signer_full_name = 'Dra. Prueba', signer_cop_number = '12345', signer_licensed_until = current_date "
                + "WHERE patient_id = " + patientId);
        }

        var view = clinicalHistoryService.getByPatientId(patientId);

        assertThat(view.status()).isEqualTo(HistoriaClinicaStatus.COMPLETED);
    }

    private Long aPatientReadyToClose(String name) {
        Long patientId = aPatientWithReadyClinicalHistory(name);
        dentalChartService.createInitial(patientId, LocalDate.now());
        dentalChartService.setSpecifications(patientId, null);
        dentalChartService.setObservations(patientId, null);
        return patientId;
    }

    // --- spec: Rechazar el sellado si un tipo que exige especificación no la tiene ---

    @Test
    void a_crown_without_the_metal_colour_in_specifications_blocks_the_closing() {
        Long patientId = aPatientReadyToClose("Corona sin especificacion"); // especificaciones revisadas y vacías
        findingService.add(patientId, new DentalChartFindingData(FindingType.CORONA, "CM", FindingCondition.GOOD,
            null, new FindingLocationData(LocationKind.TOOTH, 16, null, null, null, null)));

        assertThatThrownBy(() -> closeAdmissionService.close(patientId))
            .isInstanceOf(DentalChartChecklistIncompleteException.class)
            .satisfies(e -> assertThat(((DentalChartChecklistIncompleteException) e).getMissingFields())
                .containsExactly("specifications"));

        dentalChartService.setSpecifications(patientId, "Pieza 16: corona metálica, color plateado");
        assertThat(closeAdmissionService.close(patientId).dentalChart().status().name()).isEqualTo("SEALED");
    }

    // --- tarea 2.8a: concurrencia con hilos reales, sin deadlock (design.md D4) ---

    @Test
    void concurrent_closings_seal_exactly_once_without_deadlock() throws InterruptedException {
        Long patientId = aPatientReadyToClose("Cierre concurrente");
        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger sealed = new AtomicInteger();
        AtomicInteger rejectedAsAlreadySealed = new AtomicInteger();
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();

        try {
            for (int i = 0; i < attempts; i++) {
                pool.submit(() -> {
                    IdentityContext.set(someUser);
                    OrganizationContext.set(org);
                    ready.countDown();
                    try {
                        start.await();
                        closeAdmissionService.close(patientId);
                        sealed.incrementAndGet();
                    } catch (HistoriaClinicaSealedException | DentalChartBlockSealedException e) {
                        rejectedAsAlreadySealed.incrementAndGet();
                    } catch (Throwable e) {
                        // Un deadlock lo reporta Postgres matando una transacción (CannotAcquireLock /
                        // PessimisticLockingFailure): cae acá, no en los rechazos esperados.
                        unexpected.add(e);
                    } finally {
                        IdentityContext.clear();
                        OrganizationContext.clear();
                    }
                });
            }
            ready.await();
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).as("ningún cierre quedó colgado").isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(unexpected).as("ni deadlock ni otro error: solo un sellado y rechazos por ya sellado").isEmpty();
        assertThat(sealed.get()).as("se sella una sola vez").isEqualTo(1);
        assertThat(rejectedAsAlreadySealed.get()).isEqualTo(attempts - 1);
    }

    // --- tarea 2.8f: un hallazgo no se cuela en un odontograma que se está sellando (design.md D9) ---

    /**
     * Determinista, no dependiente del azar de los hilos: el hilo A sella dentro de una transacción
     * que mantiene ABIERTA hasta que B termina o queda esperando su bloqueo. B intenta agregar un hallazgo en ese
     * hueco. Con el bloqueo de D9, B espera al bloque bloqueado por A, ve {@code SEALED} y se
     * rechaza. Sin él, B lee el bloque todavía {@code OPEN} (A no confirmó) e inserta: el
     * odontograma quedaría sellado con un hallazgo posterior a su fecha de sellado.
     */
    @Test
    void a_finding_added_while_another_transaction_seals_is_rejected() throws Exception {
        Long patientId = aPatientReadyToClose("Hallazgo durante el sellado");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch sealedButNotCommitted = new CountDownLatch(1);
        CountDownLatch additionFinished = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<?> sealing = pool.submit(() -> {
                IdentityContext.set(someUser);
                OrganizationContext.set(org);
                try {
                    tx.executeWithoutResult(status -> {
                        closeAdmissionService.close(patientId);
                        sealedButNotCommitted.countDown();
                        // Señal explícita, no un tiempo fijo: confirma cuando B quedó ESPERANDO el
                        // bloqueo de esta transacción (con D9) o cuando B ya terminó (sin D9).
                        awaitLockWaitOrFinished(additionFinished);
                    });
                } finally {
                    IdentityContext.clear();
                    OrganizationContext.clear();
                }
            });
            Future<Throwable> addition = pool.submit(() -> {
                IdentityContext.set(someUser);
                OrganizationContext.set(org);
                try {
                    assertThat(sealedButNotCommitted.await(10, TimeUnit.SECONDS)).isTrue();
                    findingService.add(patientId, new DentalChartFindingData(FindingType.GIROVERSION, null, null,
                        null, new FindingLocationData(LocationKind.TOOTH, 21, null, null, null, null)));
                    return null;
                } catch (Throwable e) {
                    return e;
                } finally {
                    additionFinished.countDown();
                    IdentityContext.clear();
                    OrganizationContext.clear();
                }
            });

            sealing.get(30, TimeUnit.SECONDS);
            assertThat(addition.get(30, TimeUnit.SECONDS))
                .as("agregar un hallazgo a un odontograma que otra transacción está sellando se rechaza")
                .isInstanceOf(DentalChartBlockSealedException.class);
        } finally {
            pool.shutdownNow();
        }

        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             var rs = s.executeQuery("SELECT count(*) FROM dental_chart_finding f JOIN dental_chart_block b "
                 + "ON b.id = f.block_id WHERE b.patient_id = " + patientId)) {
            rs.next();
            assertThat(rs.getInt(1)).as("el odontograma sellado no tiene hallazgos posteriores").isZero();
        }
    }
}
