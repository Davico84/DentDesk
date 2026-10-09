package com.odontorisas.dentalchart;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.DentalArch;
import com.odontorisas.common.FindingCondition;
import com.odontorisas.common.FindingType;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.LocationKind;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.common.ToothSurface;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.service.dentalchart.AddFindingResult;
import com.odontorisas.service.dentalchart.DentalChartBlockSealedException;
import com.odontorisas.service.dentalchart.DentalChartFindingAlreadyExistsException;
import com.odontorisas.service.dentalchart.DentalChartFindingData;
import com.odontorisas.service.dentalchart.DentalChartFindingService;
import com.odontorisas.service.dentalchart.DentalChartFindingView;
import com.odontorisas.service.dentalchart.DentalChartService;
import com.odontorisas.service.dentalchart.FindingLocationData;
import com.odontorisas.service.dentalchart.InvalidToothRangeException;
import com.odontorisas.service.dentalchart.SurfaceOnAbsentToothException;
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
 * Hallazgos del odontograma: uno por Scenario del spec (ADR-0017 D5/D7, design.md D2/D3/D9).
 * Contra PostgreSQL real — la unicidad concurrente y el orden canónico entre piezas necesitan el
 * motor, no un mock (mismo patrón que {@code ClinicalHistoryServiceIT}).
 */
@SpringBootTest
class DentalChartFindingServiceIT extends AbstractTenantAwareIntegrationTest {

    private long org;
    private long someUser;

    @Autowired
    private DentalChartService dentalChartService;
    @Autowired
    private DentalChartFindingService findingService;
    @Autowired
    private PatientRepository patientRepository;

    @BeforeEach
    void seed() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("TRUNCATE dental_chart_finding_tooth, dental_chart_finding, "
                + "dental_chart_block, patients RESTART IDENTITY CASCADE");
            String suffix = UUID.randomUUID().toString();
            try (var rs = s.executeQuery("INSERT INTO organizations (name, slug) VALUES "
                + "('Clinica DC Finding', 'clinica-dc-finding-" + suffix + "') RETURNING id")) {
                rs.next();
                org = rs.getLong(1);
            }
            try (var rs = s.executeQuery("INSERT INTO users (email, password_hash) VALUES "
                + "('dc-finding-" + suffix + "@test.local', 'x') RETURNING id")) {
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
    Long aPatient(String name, LocalDate birthDate) {
        return patientRepository.save(Patient.builder()
            .fullName(name)
            .birthDate(birthDate)
            .status(PatientStatus.ACTIVE)
            .allergies(List.of())
            .conditions(List.of())
            .currentMedication(List.of())
            .build()).getId();
    }

    Long anAdultPatient(String name) {
        return aPatient(name, LocalDate.now().minusYears(40));
    }

    Long anOpenBlock(String patientName) {
        Long patientId = anAdultPatient(patientName);
        dentalChartService.createInitial(patientId, LocalDate.now());
        return patientId;
    }

    private static FindingLocationData surface(Integer tooth, ToothSurface s) {
        return new FindingLocationData(LocationKind.SURFACE, tooth, null, s, null, null);
    }

    private static FindingLocationData tooth(Integer t) {
        return new FindingLocationData(LocationKind.TOOTH, t, null, null, null, null);
    }

    private static FindingLocationData between(Integer a, Integer b) {
        return new FindingLocationData(LocationKind.BETWEEN_TEETH, a, b, null, null, null);
    }

    private static FindingLocationData range(List<Integer> teeth) {
        return new FindingLocationData(LocationKind.TOOTH_RANGE, null, null, null, null, teeth);
    }

    private static FindingLocationData arch(DentalArch a) {
        return new FindingLocationData(LocationKind.ARCH, null, null, null, a, null);
    }

    // --- un hallazgo por alcance ---

    @Test
    void adds_a_surface_finding() {
        Long patientId = anOpenBlock("Superficie");
        var data = new DentalChartFindingData(FindingType.LESION_CARIES, "MB", null, null,
            surface(36, ToothSurface.OCLUSAL));

        AddFindingResult result = findingService.add(patientId, data);

        assertThat(result.finding().type()).isEqualTo(FindingType.LESION_CARIES.name());
        assertThat(result.finding().location().locationKind()).isEqualTo(LocationKind.SURFACE);
    }

    @Test
    void adds_a_tooth_finding() {
        Long patientId = anOpenBlock("Pieza");
        var data = new DentalChartFindingData(FindingType.GIROVERSION, null, null, null, tooth(21));

        AddFindingResult result = findingService.add(patientId, data);

        assertThat(result.finding().location().locationKind()).isEqualTo(LocationKind.TOOTH);
    }

    @Test
    void adds_a_between_teeth_finding() {
        Long patientId = anOpenBlock("Entre piezas");
        var data = new DentalChartFindingData(FindingType.DIASTEMA, null, null, null, between(11, 21));

        AddFindingResult result = findingService.add(patientId, data);

        assertThat(result.finding().location().locationKind()).isEqualTo(LocationKind.BETWEEN_TEETH);
    }

    @Test
    void adds_a_tooth_range_finding_preserving_the_order_as_position() {
        Long patientId = anOpenBlock("Rango");
        var data = new DentalChartFindingData(FindingType.PROTESIS_PARCIAL_FIJA, null,
            FindingCondition.GOOD, null, range(List.of(34, 35, 36)));

        AddFindingResult result = findingService.add(patientId, data);

        assertThat(result.finding().location().locationKind()).isEqualTo(LocationKind.TOOTH_RANGE);
        assertThat(result.finding().location().teeth()).containsExactly(34, 35, 36);
    }

    @Test
    void adds_an_arch_finding() {
        Long patientId = anOpenBlock("Arco");
        var data = new DentalChartFindingData(FindingType.EDENTULO_TOTAL, null, null, null,
            arch(DentalArch.SUPERIOR));

        AddFindingResult result = findingService.add(patientId, data);

        assertThat(result.finding().location().locationKind()).isEqualTo(LocationKind.ARCH);
    }

    // --- rango: cardinalidad, defensa en profundidad del servicio (design.md D2) ---

    @Test
    void rejects_a_range_with_a_single_tooth_at_the_service_level() {
        Long patientId = anOpenBlock("Rango inválido");
        var data = new DentalChartFindingData(FindingType.PROTESIS_PARCIAL_FIJA, null,
            FindingCondition.GOOD, null, range(List.of(34)));

        assertThatThrownBy(() -> findingService.add(patientId, data))
            .isInstanceOf(InvalidToothRangeException.class);
    }

    @Test
    void rejects_a_duplicate_range_regardless_of_the_teeth_order() {
        Long patientId = anOpenBlock("Rango duplicado");
        findingService.add(patientId, new DentalChartFindingData(FindingType.PROTESIS_PARCIAL_FIJA, null,
            FindingCondition.GOOD, null, range(List.of(34, 35, 36))));

        assertThatThrownBy(() -> findingService.add(patientId, new DentalChartFindingData(
            FindingType.PROTESIS_PARCIAL_FIJA, null, FindingCondition.GOOD, null,
            range(List.of(36, 35, 34)))))
            .isInstanceOf(DentalChartFindingAlreadyExistsException.class);
    }

    // --- BETWEEN_TEETH: orden canónico, spec "no se duplica por el orden" ---

    @Test
    void rejects_the_same_between_teeth_finding_registered_in_reverse_order() {
        Long patientId = anOpenBlock("Orden invertido");
        findingService.add(patientId, new DentalChartFindingData(FindingType.DIASTEMA, null, null, null,
            between(11, 21)));

        assertThatThrownBy(() -> findingService.add(patientId,
            new DentalChartFindingData(FindingType.DIASTEMA, null, null, null, between(21, 11))))
            .isInstanceOf(DentalChartFindingAlreadyExistsException.class);
    }

    // --- pieza ausente con superficie ---

    @Test
    void rejects_a_surface_finding_over_an_absent_tooth() {
        Long patientId = anOpenBlock("Ausente");
        findingService.add(patientId, new DentalChartFindingData(FindingType.PIEZA_AUSENTE, "DEX", null, null,
            tooth(36)));

        assertThatThrownBy(() -> findingService.add(patientId, new DentalChartFindingData(
            FindingType.LESION_CARIES, "MB", null, null, surface(36, ToothSurface.OCLUSAL))))
            .isInstanceOf(SurfaceOnAbsentToothException.class);
    }

    // --- el implante COEXISTE con la ausencia (TOOTH, no SURFACE — ADR-0017 D7) ---

    @Test
    void an_implant_coexists_with_an_absent_tooth() {
        Long patientId = anOpenBlock("Implante");
        findingService.add(patientId, new DentalChartFindingData(FindingType.PIEZA_AUSENTE, "DEX", null, null,
            tooth(36)));

        AddFindingResult implant = findingService.add(patientId, new DentalChartFindingData(
            FindingType.IMPLANTE_DENTAL, null, FindingCondition.GOOD, null, tooth(36)));

        assertThat(implant.finding().type()).isEqualTo(FindingType.IMPLANTE_DENTAL.name());
        assertThat(findingService.listByBlock(patientId)).hasSize(2);
    }

    // --- el sellante COEXISTE con la caries en la misma superficie (ADR-0017 D7) ---

    @Test
    void a_sellant_and_a_caries_lesion_coexist_on_the_same_surface() {
        Long patientId = anOpenBlock("Sellante y caries");
        findingService.add(patientId, new DentalChartFindingData(FindingType.SELLANTE, null,
            FindingCondition.GOOD, null, surface(16, ToothSurface.OCLUSAL)));

        AddFindingResult caries = findingService.add(patientId, new DentalChartFindingData(
            FindingType.LESION_CARIES, "MB", null, null, surface(16, ToothSurface.OCLUSAL)));

        assertThat(caries.finding().type()).isEqualTo(FindingType.LESION_CARIES.name());
        assertThat(findingService.listByBlock(patientId)).hasSize(2);
    }

    // --- la giroversión no cambia por una caries vecina: sin regla de contagio (ADR-0017 D2) ---

    @Test
    void giroversion_is_unaffected_by_a_neighboring_caries_lesion() {
        Long patientId = anOpenBlock("Sin contagio");
        AddFindingResult giroversion = findingService.add(patientId, new DentalChartFindingData(
            FindingType.GIROVERSION, null, null, null, tooth(21)));

        findingService.add(patientId, new DentalChartFindingData(FindingType.LESION_CARIES, "MB", null, null,
            surface(21, ToothSurface.PALATINO)));

        DentalChartFindingView reloaded = findingService.listByBlock(patientId).stream()
            .filter(f -> f.id().equals(giroversion.finding().id()))
            .findFirst()
            .orElseThrow();
        assertThat(reloaded.type()).isEqualTo(FindingType.GIROVERSION.name());
        assertThat(reloaded.condition()).isNull();
    }

    // --- restauración en buen vs. mal estado: mismo tipo, condition distinto ---

    @Test
    void a_restoration_persists_good_and_poor_condition_independently() {
        Long patientId = anOpenBlock("Restauracion");
        AddFindingResult good = findingService.add(patientId, new DentalChartFindingData(
            FindingType.RESTAURACION_DEFINITIVA, "AM", FindingCondition.GOOD, null,
            surface(16, ToothSurface.OCLUSAL)));
        AddFindingResult poor = findingService.add(patientId, new DentalChartFindingData(
            FindingType.RESTAURACION_DEFINITIVA, "R", FindingCondition.POOR, null,
            surface(17, ToothSurface.OCLUSAL)));

        assertThat(good.finding().condition()).isEqualTo(FindingCondition.GOOD);
        assertThat(poor.finding().condition()).isEqualTo(FindingCondition.POOR);
    }

    // --- corrección con el odontograma abierto ---

    @Test
    void corrects_a_finding_while_the_block_is_open() {
        Long patientId = anOpenBlock("Correccion");
        AddFindingResult added = findingService.add(patientId, new DentalChartFindingData(
            FindingType.MOVILIDAD_PATOLOGICA, null, null, (short) 1, tooth(21)));

        AddFindingResult updated = findingService.update(patientId, added.finding().id(),
            new DentalChartFindingData(FindingType.MOVILIDAD_PATOLOGICA, null, null, (short) 3, tooth(21)));

        assertThat(updated.finding().grade()).isEqualTo((short) 3);
    }

    @Test
    void removes_a_finding_while_the_block_is_open() {
        Long patientId = anOpenBlock("Eliminacion");
        AddFindingResult added = findingService.add(patientId, new DentalChartFindingData(
            FindingType.GIROVERSION, null, null, null, tooth(21)));

        findingService.remove(patientId, added.finding().id());

        assertThat(findingService.listByBlock(patientId)).isEmpty();
    }

    // --- rechazo server-side cuando el odontograma ya está sellado (spec: solo mientras esté abierto) ---

    @Test
    void rejects_adding_a_finding_to_a_sealed_block() throws SQLException {
        Long patientId = anOpenBlock("Sellado");
        sealBlock(patientId);

        assertThatThrownBy(() -> findingService.add(patientId, new DentalChartFindingData(
            FindingType.GIROVERSION, null, null, null, tooth(21))))
            .isInstanceOf(DentalChartBlockSealedException.class);
    }

    /** SQL crudo con la conexión owner (bypass de JPA), como en {@link #seed()}: sellar el
     *  bloque es aquí un dato de FIXTURE, no una operación bajo prueba (esa es el sellado
     *  coordinado del grupo 3) — no vale la pena arrastrar la resolución de tenant de Hibernate
     *  para poner una fila en el estado que este test necesita. */
    void sealBlock(Long patientId) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("UPDATE dental_chart_block SET status = 'SEALED', sealed_at = now() "
                + "WHERE patient_id = " + patientId);
        }
    }

    // --- aviso no bloqueante de dentición↔edad (INV-15) ---

    @Test
    void warns_but_does_not_reject_a_temporary_tooth_on_an_adult_patient() {
        Long patientId = anOpenBlock("Diente temporal adulto");

        AddFindingResult result = findingService.add(patientId, new DentalChartFindingData(
            FindingType.GIROVERSION, null, null, null, tooth(55)));

        assertThat(result.denticionWarning()).isTrue();
        assertThat(findingService.listByBlock(patientId)).hasSize(1);
    }

    @Test
    void does_not_warn_for_a_permanent_tooth_on_an_adult_patient() {
        Long patientId = anOpenBlock("Diente permanente adulto");

        AddFindingResult result = findingService.add(patientId, new DentalChartFindingData(
            FindingType.GIROVERSION, null, null, null, tooth(21)));

        assertThat(result.denticionWarning()).isFalse();
    }

    // --- creación concurrente con hilos reales (spec: el mismo hallazgo no se duplica) ---

    @Test
    void two_concurrent_additions_of_the_same_finding_only_one_succeeds() throws InterruptedException {
        Long patientId = anOpenBlock("Concurrencia");
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
                        findingService.add(patientId, new DentalChartFindingData(
                            FindingType.GIROVERSION, null, null, null, tooth(21)));
                        succeeded.incrementAndGet();
                    } catch (DentalChartFindingAlreadyExistsException e) {
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
        assertThat(findingService.listByBlock(patientId)).hasSize(1);
    }
}
