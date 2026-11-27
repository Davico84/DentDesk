package com.odontorisas.tenancy;

import com.odontorisas.AbstractRlsIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba del <b>mecanismo</b> de aislamiento por ORGANIZACIÓN (design.md D10.4) sobre una
 * tabla-fixture de negocio con la política {@code organization_isolation} —la convención que
 * seguirá cada capacidad de negocio—, ejecutada como el rol de aplicación no-owner.
 *
 * <p>Cubre: "una consulta de negocio solo alcanza datos de su propia organización", "una consulta
 * que omite el filtro sigue estando aislada", "escritura cruzada rechazada" y "operación sin
 * organización en el contexto → falla".
 */
@SpringBootTest
class OrganizationRlsIT extends AbstractRlsIntegrationTest {

    private static final long ORG_A = 1L;
    private static final long ORG_B = 2L;

    @BeforeEach
    void createBusinessFixtureTable() throws SQLException {
        // Tabla de negocio de prueba con RLS por organización, creada por el owner.
        try (Connection su = ownerConnection(); Statement s = su.createStatement()) {
            s.execute("DROP TABLE IF EXISTS test_business");
            s.execute("CREATE TABLE test_business (id BIGSERIAL PRIMARY KEY, organization_id BIGINT NOT NULL, label TEXT)");
            s.execute("ALTER TABLE test_business ENABLE ROW LEVEL SECURITY");
            s.execute("ALTER TABLE test_business FORCE ROW LEVEL SECURITY");
            s.execute("CREATE POLICY organization_isolation ON test_business "
                + "USING (organization_id = current_setting('app.organization_id')::bigint)");
            // Semilla como owner (el superusuario salta RLS): 2 filas de A, 1 de B.
            s.execute("INSERT INTO test_business(organization_id, label) VALUES "
                + "(" + ORG_A + ", 'a1'), (" + ORG_A + ", 'a2'), (" + ORG_B + ", 'b1')");
        }
        // Asegura el rol de app y concédele acceso a la tabla nueva (no cubierta por el GRANT inicial).
        try (Connection app = appConnection()) {
            assertThat(app.isValid(2)).isTrue();
        }
        try (Connection su = ownerConnection(); Statement s = su.createStatement()) {
            s.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON test_business TO " + APP_USER);
            s.execute("GRANT USAGE, SELECT ON test_business_id_seq TO " + APP_USER);
        }
    }

    private List<String> labelsVisibleTo(long organizationId) throws SQLException {
        try (Connection app = appConnection(); Statement s = app.createStatement()) {
            s.execute("SELECT set_config('app.organization_id', '" + organizationId + "', false)");
            List<String> labels = new ArrayList<>();
            try (ResultSet rs = s.executeQuery("SELECT label FROM test_business")) {
                while (rs.next()) {
                    labels.add(rs.getString(1));
                }
            }
            return labels;
        }
    }

    /** Sin esto la tabla queda en el esquema compartido y {@code TenantTableInventoryIT} la ve sin clasificar. */
    @AfterEach
    void dropTestBusiness() throws SQLException {
        try (Connection su = ownerConnection(); Statement s = su.createStatement()) {
            s.execute("DROP TABLE IF EXISTS test_business");
        }
    }

    @Test
    void a_business_query_only_reaches_its_own_organization() throws SQLException {
        assertThat(labelsVisibleTo(ORG_A)).containsExactlyInAnyOrder("a1", "a2");
        assertThat(labelsVisibleTo(ORG_B)).containsExactly("b1");
    }

    @Test
    void a_query_without_organization_filter_is_still_isolated() throws SQLException {
        // "SELECT label FROM test_business" no lleva WHERE organization_id; RLS lo aísla igual.
        assertThat(labelsVisibleTo(ORG_A)).doesNotContain("b1");
    }

    @Test
    void a_cross_tenant_write_is_rejected() throws SQLException {
        try (Connection app = appConnection(); Statement s = app.createStatement()) {
            s.execute("SELECT set_config('app.organization_id', '" + ORG_A + "', false)");
            // Insertar una fila de la organización B desde el contexto de A: rechazado por la policy.
            assertThatThrownBy(() -> s.executeUpdate(
                    "INSERT INTO test_business(organization_id, label) VALUES (" + ORG_B + ", 'intruso')"))
                .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void a_business_query_without_organization_context_fails_closed() throws SQLException {
        try (Connection app = appConnection(); Statement s = app.createStatement()) {
            assertThatThrownBy(() -> s.executeQuery("SELECT label FROM test_business"))
                .isInstanceOf(SQLException.class);
        }
    }
}
