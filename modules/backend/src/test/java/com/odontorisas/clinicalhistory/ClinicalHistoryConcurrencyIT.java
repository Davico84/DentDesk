package com.odontorisas.clinicalhistory;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.DiagnosisLocation;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.HistoriaClinicaRepository;
import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.service.admission.CloseAdmissionService;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryData;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryDiagnosisService;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryService;
import com.odontorisas.service.clinicalhistory.DiagnosisData;
import com.odontorisas.service.clinicalhistory.HistoriaClinicaSealedException;
import com.odontorisas.service.dentalchart.DentalChartService;
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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `update-clinical-history`: ninguna escritura de la información inicial reabre una Historia que
 * se está sellando ni escribe en ella (design.md D1/D3). Hilos reales contra PostgreSQL, con el
 * orden FORZADO: el hilo A toma el bloqueo de la Historia (sellando, o sosteniéndolo para el caso
 * de rollback) y recién entonces libera al hilo B; A confirma cuando B queda esperando ese bloqueo
 * ({@code pg_locks}). Sin el fix, B no espera nada y escribe: los tests de sellado vs. escritura
 * fallan.
 */
@SpringBootTest
class ClinicalHistoryConcurrencyIT extends AbstractTenantAwareIntegrationTest {

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
    private HistoriaClinicaRepository historiaClinicaRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final ClinicalHistoryData FULL_HC = new ClinicalHistoryData(
        "Dolor de muela", "Desde hace 3 días", "Ninguno relevante", "Sin hallazgos anormales",
        "Favorable", null, true, null, true, null, true, null);

    private static ClinicalHistoryData withMotivo(String motivo, String enfermedad) {
        return new ClinicalHistoryData(motivo, enfermedad, "Ninguno relevante", "Sin hallazgos anormales",
            "Favorable", null, true, null, true, null, true, null);
    }

    private static final DiagnosisData A00 = new DiagnosisData("A00", DiagnosisLocation.GENERAL, null, TipoDiagnostico.PRESUNTIVO);
    private static final DiagnosisData A01 = new DiagnosisData("A01", DiagnosisLocation.GENERAL, null, TipoDiagnostico.DEFINITIVO);

    @BeforeEach
    void seed() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("TRUNCATE dental_chart_finding_tooth, dental_chart_finding, dental_chart_block, "
                + "clinical_history_diagnosis, historia_clinica, patients RESTART IDENTITY CASCADE");
            String suffix = UUID.randomUUID().toString();
            try (var rs = s.executeQuery("INSERT INTO organizations (name, slug) VALUES "
                + "('Clinica Concurrencia HC', 'clinica-conc-hc-" + suffix + "') RETURNING id")) {
                rs.next();
                org = rs.getLong(1);
            }
            try (var rs = s.executeQuery("INSERT INTO users (email, password_hash) VALUES "
                + "('conc-hc-" + suffix + "@test.local', 'x') RETURNING id")) {
                rs.next();
                someUser = rs.getLong(1);
            }
        }
        asClinic();
    }

    @AfterEach
    void clearContext() {
        IdentityContext.clear();
        OrganizationContext.clear();
    }

    private void asClinic() {
        IdentityContext.set(someUser);
        OrganizationContext.set(org);
    }

    /** Paciente con Historia lista para sellar (1 diagnóstico A00) y odontograma inicial revisado. */
    private Long aPatientReadyToClose() {
        Long patientId = patientRepository.save(Patient.builder()
            .fullName("Paciente concurrencia")
            .birthDate(LocalDate.of(1990, 1, 1))
            .status(PatientStatus.ACTIVE)
            .allergies(List.of())
            .conditions(List.of())
            .currentMedication(List.of())
            .build()).getId();
        clinicalHistoryService.create(patientId);
        clinicalHistoryService.update(patientId, FULL_HC);
        diagnosisService.add(patientId, A00);
        dentalChartService.createInitial(patientId, LocalDate.now());
        dentalChartService.setSpecifications(patientId, null);
        dentalChartService.setObservations(patientId, null);
        return patientId;
    }

    /**
     * A ejecuta {@code holdLock} dentro de una transacción (que ya tiene el bloqueo de la Historia
     * cuando termina), libera a B, espera a que B quede bloqueado o termine y recién entonces cierra
     * la transacción: confirma, o hace rollback si {@code rollback}. Devuelve lo que lanzó B
     * ({@code null} si B terminó bien).
     */
    private Throwable whileHoldingTheLock(Consumer<Long> holdLock, Long patientId, boolean rollback,
                                          Callable<?> write) throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch writeFinished = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = pool.submit(() -> {
                asClinic();
                try {
                    tx.executeWithoutResult(status -> {
                        holdLock.accept(patientId);
                        locked.countDown();
                        awaitLockWaitOrFinished(writeFinished);
                        if (rollback) {
                            status.setRollbackOnly();
                        }
                    });
                } finally {
                    clearContext();
                }
            });
            Future<Throwable> writer = pool.submit(() -> {
                asClinic();
                try {
                    assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                    write.call();
                    return null;
                } catch (Throwable e) {
                    return e;
                } finally {
                    writeFinished.countDown();
                    clearContext();
                }
            });
            holder.get(30, TimeUnit.SECONDS);
            return writer.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private Throwable whileSealing(Long patientId, Callable<?> write) throws Exception {
        return whileHoldingTheLock(closeAdmissionService::close, patientId, false, write);
    }

    private String historia(String column, Long patientId) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             var rs = s.executeQuery("SELECT " + column + " FROM historia_clinica WHERE patient_id = " + patientId)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private List<String> diagnosisCodes(Long patientId) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             var rs = s.executeQuery("SELECT d.cie10_code FROM clinical_history_diagnosis d JOIN historia_clinica h "
                 + "ON h.id = d.historia_id WHERE h.patient_id = " + patientId + " ORDER BY d.id")) {
            List<String> codes = new java.util.ArrayList<>();
            while (rs.next()) {
                codes.add(rs.getString(1));
            }
            return codes;
        }
    }

    // --- 1.1 · scenario: Guardar la Historia mientras otra operación la sella no la reabre ---

    @Test
    void saving_the_clinical_history_while_it_is_being_sealed_does_not_reopen_it() throws Exception {
        Long patientId = aPatientReadyToClose();

        Throwable result = whileSealing(patientId,
            () -> clinicalHistoryService.update(patientId, withMotivo("Cambio tardío", "Tardío")));

        assertThat(result).isInstanceOf(HistoriaClinicaSealedException.class);
        assertThat(historia("status", patientId)).isEqualTo("COMPLETED");
        assertThat(historia("motivo_consulta", patientId)).isEqualTo("Dolor de muela");
    }

    // --- 1.2-1.4 · scenario: Los diagnósticos de una Historia que se está sellando no cambian ---

    @Test
    void adding_a_diagnosis_while_the_clinical_history_is_being_sealed_is_rejected() throws Exception {
        Long patientId = aPatientReadyToClose();

        Throwable result = whileSealing(patientId, () -> diagnosisService.add(patientId, A01));

        assertThat(result).isInstanceOf(HistoriaClinicaSealedException.class);
        assertThat(diagnosisCodes(patientId)).containsExactly("A00");
    }

    @Test
    void editing_a_diagnosis_while_the_clinical_history_is_being_sealed_is_rejected() throws Exception {
        Long patientId = aPatientReadyToClose();
        Long diagnosisId = diagnosisService.listByPatient(patientId).getFirst().id();

        Throwable result = whileSealing(patientId, () -> diagnosisService.update(patientId, diagnosisId, A01));

        assertThat(result).isInstanceOf(HistoriaClinicaSealedException.class);
        assertThat(diagnosisCodes(patientId)).containsExactly("A00");
    }

    @Test
    void removing_a_diagnosis_while_the_clinical_history_is_being_sealed_is_rejected() throws Exception {
        Long patientId = aPatientReadyToClose();
        Long diagnosisId = diagnosisService.listByPatient(patientId).getFirst().id();

        Throwable result = whileSealing(patientId, () -> {
            diagnosisService.remove(patientId, diagnosisId);
            return null;
        });

        assertThat(result).isInstanceOf(HistoriaClinicaSealedException.class);
        assertThat(diagnosisCodes(patientId)).containsExactly("A00");
    }

    // --- 1.5 · dos guardados concurrentes en DRAFT: se serializan y gana el último ---

    @Test
    void two_concurrent_saves_in_draft_both_succeed_and_the_last_one_wins_whole() throws Exception {
        Long patientId = aPatientReadyToClose();
        ClinicalHistoryData first = withMotivo("Motivo uno", "Enfermedad uno");
        ClinicalHistoryData second = withMotivo("Motivo dos", "Enfermedad dos");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> saves = List.of(first, second).stream()
                .map(data -> pool.submit(() -> {
                    asClinic();
                    try {
                        start.await();
                        clinicalHistoryService.update(patientId, data);
                        return (Throwable) null;
                    } catch (Throwable e) {
                        return e;
                    } finally {
                        clearContext();
                    }
                }))
                .toList();
            start.countDown();
            for (Future<Throwable> save : saves) {
                assertThat(save.get(30, TimeUnit.SECONDS)).as("ningún guardado falla por el bloqueo").isNull();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(historia("status", patientId)).isEqualTo("DRAFT");
        String motivo = historia("motivo_consulta", patientId);
        String enfermedad = historia("enfermedad_actual", patientId);
        assertThat(motivo).isIn("Motivo uno", "Motivo dos");
        assertThat(enfermedad).as("los datos son los de UN guardado, no una mezcla")
            .isEqualTo(motivo.equals("Motivo uno") ? "Enfermedad uno" : "Enfermedad dos");
    }

    // --- 1.6 · un sellado que falla libera el bloqueo y la escritura continúa ---

    @Test
    void a_sealing_that_rolls_back_releases_the_lock_and_the_save_goes_through() throws Exception {
        Long patientId = aPatientReadyToClose();

        // A sostiene el mismo bloqueo que toma el sellado y hace rollback, como un sellado que
        // falla después de bloquear (p. ej. por el checklist del odontograma).
        Throwable result = whileHoldingTheLock(id -> historiaClinicaRepository.lockByPatientId(id), patientId, true,
            () -> clinicalHistoryService.update(patientId, withMotivo("Tras el rollback", "Continúa")));

        assertThat(result).isNull();
        assertThat(historia("status", patientId)).isEqualTo("DRAFT");
        assertThat(historia("motivo_consulta", patientId)).isEqualTo("Tras el rollback");
    }
}
