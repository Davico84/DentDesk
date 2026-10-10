package com.odontorisas.dentalchart;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.DentalChartBlockStatus;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.DentalChartBlockRepository;
import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.service.dentalchart.DentalChartBlockAlreadyExistsException;
import com.odontorisas.service.dentalchart.DentalChartBlockView;
import com.odontorisas.service.dentalchart.DentalChartService;
import com.odontorisas.service.patient.PatientNotFoundException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Creación del odontograma inicial y sus dos campos de texto (design.md D3/D5/D9, ADR-0012,
 * ADR-0017 D6, spec: <i>Odontograma inicial único por paciente y clínica</i>). Contra PostgreSQL
 * real — la unicidad concurrente necesita el motor, no un mock (mismo patrón que
 * {@code ClinicalHistoryServiceIT}).
 */
@SpringBootTest
class DentalChartServiceIT extends AbstractTenantAwareIntegrationTest {

    private long org;
    private long someUser;

    @Autowired
    private DentalChartService dentalChartService;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private DentalChartBlockRepository blockRepository;

    @BeforeEach
    void seed() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("TRUNCATE dental_chart_finding_tooth, dental_chart_finding, "
                + "dental_chart_block, patients RESTART IDENTITY CASCADE");
            String suffix = UUID.randomUUID().toString();
            try (var rs = s.executeQuery("INSERT INTO organizations (name, slug) VALUES "
                + "('Clinica DC Service', 'clinica-dc-service-" + suffix + "') RETURNING id")) {
                rs.next();
                org = rs.getLong(1);
            }
            try (var rs = s.executeQuery("INSERT INTO users (email, password_hash) VALUES "
                + "('dc-service-" + suffix + "@test.local', 'x') RETURNING id")) {
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

    @Test
    void creates_the_first_dental_chart_block_of_a_patient_open() {
        Long patientId = aPatient("Ada");

        DentalChartBlockView view = dentalChartService.createInitial(patientId, LocalDate.now());

        assertThat(view.status()).isEqualTo(DentalChartBlockStatus.OPEN);
        assertThat(view.patientId()).isEqualTo(patientId);
    }

    @Test
    void rejects_a_second_dental_chart_block_for_the_same_patient() {
        Long patientId = aPatient("Ada");
        dentalChartService.createInitial(patientId, LocalDate.now());

        assertThatThrownBy(() -> dentalChartService.createInitial(patientId, LocalDate.now()))
            .isInstanceOf(DentalChartBlockAlreadyExistsException.class);
        assertThat(blockRepository.findAll()).hasSize(1);
    }

    @Test
    void rejects_creation_for_a_nonexistent_patient() {
        assertThatThrownBy(() -> dentalChartService.createInitial(999L, LocalDate.now()))
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
                    IdentityContext.set(someUser);
                    OrganizationContext.set(org);
                    ready.countDown();
                    try {
                        start.await();
                        dentalChartService.createInitial(patientId, LocalDate.now());
                        succeeded.incrementAndGet();
                    } catch (DentalChartBlockAlreadyExistsException e) {
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
        assertThat(blockRepository.findAll()).hasSize(1);
    }

    @Test
    void registers_specifications_as_reviewed_even_with_no_text() {
        Long patientId = aPatient("Sin especificaciones");
        dentalChartService.createInitial(patientId, LocalDate.now());

        DentalChartBlockView view = dentalChartService.setSpecifications(patientId, null);

        assertThat(view.specificationsReviewed()).isTrue();
        assertThat(view.specifications()).isNull();
    }

    @Test
    void registers_observations_separately_from_specifications() {
        Long patientId = aPatient("Con observaciones");
        dentalChartService.createInitial(patientId, LocalDate.now());

        dentalChartService.setSpecifications(patientId, "Movilidad grado 2 en pieza 21.");
        DentalChartBlockView view = dentalChartService.setObservations(patientId, "Frenillo lingual corto.");

        assertThat(view.specifications()).isEqualTo("Movilidad grado 2 en pieza 21.");
        assertThat(view.observations()).isEqualTo("Frenillo lingual corto.");
        assertThat(view.specificationsReviewed()).isTrue();
        assertThat(view.observationsReviewed()).isTrue();
    }
}
