package com.odontorisas.dentalchart;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.DentalChartBlockStatus;
import com.odontorisas.common.DentalChartBlockType;
import com.odontorisas.common.FindingType;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.LocationKind;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.persistence.entity.DentalChartBlock;
import com.odontorisas.persistence.entity.DentalChartFinding;
import com.odontorisas.persistence.entity.DentalChartFindingTooth;
import com.odontorisas.persistence.entity.DentalChartLocation;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.DentalChartBlockRepository;
import com.odontorisas.persistence.repository.DentalChartFindingRepository;
import com.odontorisas.persistence.repository.DentalChartFindingToothRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de aislamiento de <b>MOTOR</b> (RLS) para las <b>TRES</b> tablas de negocio de este change:
 * {@code dental_chart_block}, {@code dental_chart_finding} y {@code dental_chart_finding_tooth}
 * (design.md D1/D2). La RLS de Postgres <b>no se hereda por FK</b> — que una tabla cuelgue de otra
 * ya aislada no la protege; es el hallazgo pagado en {@code add-clinical-history} y repetido a
 * propósito en cada tabla nueva, por eso las tres se prueban por separado (spec: <i>Aislamiento
 * por clínica</i>).
 *
 * <p>Mismo patrón que {@code ClinicalHistoryRlsIT}: rol de aplicación no-superusuario, contexto
 * poblado como lo haría el filtro JWT, sin llamar nunca a {@code TenantSession.bind(...)}.
 */
@SpringBootTest
class DentalChartRlsIT extends AbstractTenantAwareIntegrationTest {

    private long orgA;
    private long orgB;
    private long someUser;

    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private DentalChartBlockRepository blockRepository;
    @Autowired
    private DentalChartFindingRepository findingRepository;
    @Autowired
    private DentalChartFindingToothRepository findingToothRepository;

    @BeforeEach
    void seedOrganizationsAndUsers() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("TRUNCATE dental_chart_finding_tooth, dental_chart_finding, "
                + "dental_chart_block, patients RESTART IDENTITY CASCADE");
            String suffix = UUID.randomUUID().toString();
            try (var rs = s.executeQuery("INSERT INTO organizations (name, slug) VALUES "
                + "('Clinica A', 'clinica-a-dc-" + suffix + "') RETURNING id")) {
                rs.next();
                orgA = rs.getLong(1);
            }
            try (var rs = s.executeQuery("INSERT INTO organizations (name, slug) VALUES "
                + "('Clinica B', 'clinica-b-dc-" + suffix + "') RETURNING id")) {
                rs.next();
                orgB = rs.getLong(1);
            }
            try (var rs = s.executeQuery("INSERT INTO users (email, password_hash) VALUES "
                + "('someone-dc-" + suffix + "@test.local', 'x') RETURNING id")) {
                rs.next();
                someUser = rs.getLong(1);
            }
        }
    }

    @AfterEach
    void clearContext() {
        IdentityContext.clear();
        OrganizationContext.clear();
    }

    private void asClinic(long organizationId) {
        IdentityContext.set(someUser);
        OrganizationContext.set(organizationId);
    }

    @Transactional
    Long createPatientAndBlock(String fullName) {
        Patient patient = patientRepository.save(Patient.builder()
            .fullName(fullName)
            .birthDate(LocalDate.of(1990, 1, 1))
            .status(PatientStatus.ACTIVE)
            .allergies(List.of())
            .conditions(List.of())
            .currentMedication(List.of())
            .build());
        DentalChartBlock block = blockRepository.save(DentalChartBlock.builder()
            .patientId(patient.getId())
            .type(DentalChartBlockType.INITIAL)
            .status(DentalChartBlockStatus.OPEN)
            .examinedOn(LocalDate.now())
            .build());
        return block.getId();
    }

    @Transactional
    Long addToothFinding(Long blockId, int tooth) {
        DentalChartFinding finding = findingRepository.save(DentalChartFinding.builder()
            .blockId(blockId)
            .type(FindingType.GIROVERSION.name())
            .subtype("")
            .location(new DentalChartLocation(LocationKind.TOOTH, tooth, null, null, null))
            .build());
        return finding.getId();
    }

    @Transactional
    Long addRangeFinding(Long blockId, int... teeth) {
        DentalChartFinding finding = findingRepository.save(DentalChartFinding.builder()
            .blockId(blockId)
            .type(FindingType.PROTESIS_PARCIAL_FIJA.name())
            .subtype("")
            .location(new DentalChartLocation(LocationKind.TOOTH_RANGE, null, null, null, null))
            .build());
        int position = 1;
        for (int tooth : teeth) {
            findingToothRepository.save(DentalChartFindingTooth.builder()
                .findingId(finding.getId())
                .position(position++)
                .tooth(tooth)
                .build());
        }
        return finding.getId();
    }

    @Transactional
    Long createPatient(String fullName) {
        return patientRepository.save(Patient.builder()
            .fullName(fullName)
            .birthDate(LocalDate.of(1990, 1, 1))
            .status(PatientStatus.ACTIVE)
            .allergies(List.of())
            .conditions(List.of())
            .currentMedication(List.of())
            .build()).getId();
    }

    // --- dental_chart_block ---

    @Test
    void a_clinic_only_sees_its_own_dental_chart_block() {
        asClinic(orgA);
        Long blockA = createPatientAndBlock("Paciente de A");

        asClinic(orgB);
        createPatientAndBlock("Paciente de B");

        assertThat(blockRepository.findAll()).hasSize(1);

        asClinic(orgA);
        assertThat(blockRepository.findAll()).hasSize(1);
        assertThat(blockRepository.findById(blockA)).isPresent();
    }

    @Test
    void a_clinic_cannot_read_a_block_of_another_clinic_even_by_id() {
        asClinic(orgA);
        Long blockA = createPatientAndBlock("Paciente de A");

        asClinic(orgB);
        assertThat(blockRepository.findById(blockA)).isEmpty();
    }

    @Test
    void an_operation_without_context_fails_closed_for_blocks() {
        assertThatThrownBy(() -> blockRepository.findAll())
            .as("una consulta de odontogramas sin contexto debe FALLAR, no devolver vacío")
            .isInstanceOf(Exception.class);
    }

    // --- dental_chart_finding — RLS PROPIA, no heredada del bloque ---

    @Test
    void a_clinic_only_sees_its_own_findings() {
        asClinic(orgA);
        Long blockA = createPatientAndBlock("Paciente Finding A");
        addToothFinding(blockA, 36);

        asClinic(orgB);
        assertThat(findingRepository.findAll()).isEmpty();

        asClinic(orgA);
        assertThat(findingRepository.findAll()).hasSize(1);
    }

    @Test
    void an_operation_without_context_fails_closed_for_findings() {
        assertThatThrownBy(() -> findingRepository.findAll())
            .as("una consulta de hallazgos sin contexto debe FALLAR, no devolver vacío")
            .isInstanceOf(Exception.class);
    }

    // --- dental_chart_finding_tooth — RLS PROPIA, no heredada del finding ---

    @Test
    void a_clinic_only_sees_its_own_finding_tooth_rows() {
        asClinic(orgA);
        Long blockA = createPatientAndBlock("Paciente Rango A");
        addRangeFinding(blockA, 34, 35, 36);

        asClinic(orgB);
        assertThat(findingToothRepository.findAll()).isEmpty();

        asClinic(orgA);
        assertThat(findingToothRepository.findAll()).hasSize(3);
    }

    @Test
    void an_operation_without_context_fails_closed_for_finding_tooth() {
        assertThatThrownBy(() -> findingToothRepository.findAll())
            .as("una consulta de piezas de rango sin contexto debe FALLAR, no devolver vacío")
            .isInstanceOf(Exception.class);
    }

    // --- FK compuestas por tenant (tarea 2.8d, design.md D1) ---
    //
    // La RLS impide VER y ESCRIBIR filas de otra clínica; no impide que una fila sea internamente
    // incoherente (declarar la organización A y apuntar a un padre de B). Eso lo ata la FK
    // compuesta (organization_id, parent_id). Las inserciones van por la conexión OWNER, que es
    // superusuario y se salta la RLS: así la ÚNICA barrera que queda es la FK — exactamente la
    // propiedad que se prueba (las comprobaciones internas de FK de Postgres tampoco respetan la
    // RLS). Cada caso lleva su control: la misma fila con la organización correcta SÍ entra, para
    // que el rechazo no pueda venir de otra constraint.

    private static void assertCompositeFkViolation(Throwable e, String constraint) {
        assertThat(e).isInstanceOf(SQLException.class);
        assertThat(((SQLException) e).getSQLState()).as("foreign_key_violation").isEqualTo("23503");
        assertThat(e.getMessage()).contains(constraint);
    }

    private String insertToothFinding(long organizationId, long blockId, int tooth) {
        return "INSERT INTO dental_chart_finding (organization_id, block_id, type, subtype, location_kind, "
            + "tooth_a, created_by, updated_by) VALUES (" + organizationId + ", " + blockId
            + ", 'GIROVERSION', '', 'TOOTH', " + tooth + ", " + someUser + ", " + someUser + ")";
    }

    @Test
    void a_finding_cannot_point_to_a_block_of_another_clinic_even_declaring_its_own_organization()
        throws SQLException {
        asClinic(orgB);
        Long blockB = createPatientAndBlock("Paciente de B");

        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute(insertToothFinding(orgB, blockB, 11)); // control: misma organización, entra

            assertThatThrownBy(() -> s.execute(insertToothFinding(orgA, blockB, 12)))
                .satisfies(e -> assertCompositeFkViolation(e, "fk_dental_chart_finding_block"));
        }
    }

    @Test
    void a_range_tooth_cannot_point_to_a_finding_of_another_clinic() throws SQLException {
        asClinic(orgB);
        Long findingB = addRangeFinding(createPatientAndBlock("Rango de B"), 34, 35);

        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            String insert = "INSERT INTO dental_chart_finding_tooth (organization_id, finding_id, position, "
                + "tooth, created_by, updated_by) VALUES (%d, " + findingB + ", %d, %d, " + someUser + ", "
                + someUser + ")";
            s.execute(insert.formatted(orgB, 3, 36)); // control

            assertThatThrownBy(() -> s.execute(insert.formatted(orgA, 4, 37)))
                .satisfies(e -> assertCompositeFkViolation(e, "fk_dental_chart_finding_tooth_finding"));
        }
    }

    @Test
    void a_block_cannot_point_to_a_patient_of_another_clinic() throws SQLException {
        asClinic(orgB);
        Long patientB = createPatient("Paciente sin odontograma de B");

        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            String insert = "INSERT INTO dental_chart_block (organization_id, patient_id, examined_on, "
                + "created_by, updated_by) VALUES (%d, " + patientB + ", CURRENT_DATE, " + someUser + ", "
                + someUser + ")";

            assertThatThrownBy(() -> s.execute(insert.formatted(orgA)))
                .satisfies(e -> assertCompositeFkViolation(e, "fk_dental_chart_block_patient"));

            s.execute(insert.formatted(orgB)); // control: después, para no ocupar el único INITIAL del paciente
        }
    }
}
