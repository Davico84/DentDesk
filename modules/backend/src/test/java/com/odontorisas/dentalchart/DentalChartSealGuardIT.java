package com.odontorisas.dentalchart;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.FindingCondition;
import com.odontorisas.common.FindingType;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.LocationKind;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.service.dentalchart.DentalChartBlockSealedException;
import com.odontorisas.service.dentalchart.DentalChartFindingData;
import com.odontorisas.service.dentalchart.DentalChartFindingService;
import com.odontorisas.service.dentalchart.DentalChartService;
import com.odontorisas.service.dentalchart.FindingLocationData;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El sellado se protege también en la persistencia (design.md D9, migración V20). Cubre los
 * scenarios "Rechazar la reapertura de un odontograma sellado", "Rechazar el segundo sellado" (la
 * fecha original no cambia) y "Rechazar la modificación de las piezas de un rango sellado": primero
 * por el servicio (la ruta normal) y después con SQL crudo por la conexión owner, que no pasa por
 * ningún servicio — ahí la única barrera son los triggers.
 */
@SpringBootTest
class DentalChartSealGuardIT extends AbstractTenantAwareIntegrationTest {

    private long org;
    private long someUser;
    private Long patientId;
    private long blockId;
    private long rangeFindingId;

    @Autowired
    private DentalChartService dentalChartService;
    @Autowired
    private DentalChartFindingService findingService;
    @Autowired
    private PatientRepository patientRepository;

    private static final FindingLocationData RANGE_14_TO_12 =
        new FindingLocationData(LocationKind.TOOTH_RANGE, null, null, null, null, List.of(14, 13, 12));

    @BeforeEach
    void aSealedDentalChartWithARange() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("TRUNCATE dental_chart_finding_tooth, dental_chart_finding, dental_chart_block, "
                + "patients RESTART IDENTITY CASCADE");
            String suffix = UUID.randomUUID().toString();
            try (var rs = s.executeQuery("INSERT INTO organizations (name, slug) VALUES "
                + "('Clinica Sellado', 'clinica-sellado-" + suffix + "') RETURNING id")) {
                rs.next();
                org = rs.getLong(1);
            }
            try (var rs = s.executeQuery("INSERT INTO users (email, password_hash) VALUES "
                + "('sellado-" + suffix + "@test.local', 'x') RETURNING id")) {
                rs.next();
                someUser = rs.getLong(1);
            }
        }
        IdentityContext.set(someUser);
        OrganizationContext.set(org);

        patientId = patientRepository.save(Patient.builder()
            .fullName("Paciente sellado")
            .birthDate(LocalDate.of(1980, 1, 1))
            .status(PatientStatus.ACTIVE)
            .allergies(List.of())
            .conditions(List.of())
            .currentMedication(List.of())
            .build()).getId();
        blockId = dentalChartService.createInitial(patientId, LocalDate.now()).id();
        rangeFindingId = findingService.add(patientId, new DentalChartFindingData(
            FindingType.PROTESIS_PARCIAL_FIJA, null, FindingCondition.GOOD, null, RANGE_14_TO_12)).finding().id();
        dentalChartService.setSpecifications(patientId, null);
        dentalChartService.setObservations(patientId, null);
        // Directo, sin el coordinador: acá el objeto de prueba es el odontograma, no la admisión
        // (AdmissionSealingArchitectureTest solo mira código de producción).
        dentalChartService.sealInitial(patientId);
    }

    @AfterEach
    void clearContext() {
        IdentityContext.clear();
        OrganizationContext.clear();
    }

    private String sealedAt() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             var rs = s.executeQuery("SELECT sealed_at::text FROM dental_chart_block WHERE id = " + blockId)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private void assertRejectedAsSealed(String sql) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            assertThatThrownBy(() -> s.execute(sql))
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState())
                    .as("object_not_in_prerequisite_state, del trigger de V20").isEqualTo("55000"));
        }
    }

    // --- spec: Rechazar la reapertura de un odontograma sellado ---

    @Test
    void a_sealed_dental_chart_cannot_be_reopened_even_with_direct_sql() throws SQLException {
        assertRejectedAsSealed("UPDATE dental_chart_block SET status = 'OPEN', sealed_at = NULL WHERE id = " + blockId);
        assertRejectedAsSealed("DELETE FROM dental_chart_block WHERE id = " + blockId);
    }

    // --- spec: Rechazar el segundo sellado (y la fecha original no cambia) ---

    @Test
    void a_second_sealing_is_rejected_and_the_original_date_does_not_change() throws SQLException {
        String original = sealedAt();

        assertThatThrownBy(() -> dentalChartService.sealInitial(patientId))
            .isInstanceOf(DentalChartBlockSealedException.class);
        assertRejectedAsSealed("UPDATE dental_chart_block SET sealed_at = now() WHERE id = " + blockId);

        assertThat(sealedAt()).isEqualTo(original);
    }

    // --- spec: Rechazar la modificación de las piezas de un rango sellado ---

    @Test
    void the_teeth_of_a_sealed_range_cannot_be_modified() throws SQLException {
        assertThatThrownBy(() -> findingService.update(patientId, rangeFindingId, new DentalChartFindingData(
            FindingType.PROTESIS_PARCIAL_FIJA, null, FindingCondition.GOOD, null,
            new FindingLocationData(LocationKind.TOOTH_RANGE, null, null, null, null, List.of(14, 13)))))
            .isInstanceOf(DentalChartBlockSealedException.class);

        assertRejectedAsSealed("UPDATE dental_chart_finding_tooth SET tooth = 11 WHERE finding_id = " + rangeFindingId);
        assertRejectedAsSealed("DELETE FROM dental_chart_finding_tooth WHERE finding_id = " + rangeFindingId);
        assertRejectedAsSealed("INSERT INTO dental_chart_finding_tooth (organization_id, finding_id, position, tooth, "
            + "created_by, updated_by) VALUES (" + org + ", " + rangeFindingId + ", 4, 11, " + someUser + ", " + someUser + ")");
    }

    @Test
    void findings_of_a_sealed_dental_chart_cannot_be_added_changed_or_removed() throws SQLException {
        assertRejectedAsSealed("INSERT INTO dental_chart_finding (organization_id, block_id, type, subtype, location_kind, "
            + "tooth_a, created_by, updated_by) VALUES (" + org + ", " + blockId + ", 'GIROVERSION', '', 'TOOTH', 21, "
            + someUser + ", " + someUser + ")");
        assertRejectedAsSealed("UPDATE dental_chart_finding SET condition = 'POOR' WHERE id = " + rangeFindingId);
        assertRejectedAsSealed("DELETE FROM dental_chart_finding WHERE id = " + rangeFindingId);
    }
}
