package com.odontorisas.tenancy;

import com.odontorisas.AbstractRlsIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las consultas del rol de sesión del inventario (design D7) y la de objetos en otros esquemas (D8),
 * contra PostgreSQL real. Cada caso abre <b>su propia conexión como el rol de prueba</b>, porque las
 * consultas evalúan {@code session_user}: el rol de la app no sirve para probar los casos que fallan.
 * Los roles y el esquema de prueba se crean con el superusuario del contenedor y se borran al final.
 */
class TenantCatalogRoleQueriesIT extends AbstractRlsIntegrationTest {

    private static final String PASSWORD = "probe_pwd";

    private final String prefix = "zz_" + UUID.randomUUID().toString().substring(0, 8) + "_";
    private final List<String> roles = new ArrayList<>();
    private String probeSchema;

    @AfterEach
    void dropProbes() throws SQLException {
        try (Connection su = ownerConnection(); Statement s = su.createStatement()) {
            if (probeSchema != null) {
                s.execute("DROP SCHEMA IF EXISTS " + probeSchema + " CASCADE");
            }
            for (String role : roles.reversed()) {
                s.execute("DROP ROLE IF EXISTS " + role);
            }
        }
    }

    private String role(String name, String attributes) throws SQLException {
        String role = prefix + name;
        sql("CREATE ROLE " + role + " " + attributes
                + (attributes.contains("NOLOGIN") ? "" : " LOGIN PASSWORD '" + PASSWORD + "'"));
        roles.add(role);
        return role;
    }

    private void sql(String statement) throws SQLException {
        try (Connection su = ownerConnection(); Statement s = su.createStatement()) {
            s.execute(statement);
        }
    }

    private List<String> as(String role, String query) throws SQLException {
        try (Connection c = DriverManager.getConnection(ownerJdbcUrl(), role, PASSWORD)) {
            return TenantCatalogReader.strings(c, query);
        }
    }

    @Test
    void a_superuser_fails() throws SQLException {
        String role = role("super", "SUPERUSER");
        assertThat(as(role, TenantCatalogReader.BYPASS_ROLES)).contains(role);
    }

    @Test
    void a_bypassrls_role_fails() throws SQLException {
        String role = role("bypass", "NOSUPERUSER BYPASSRLS");
        assertThat(as(role, TenantCatalogReader.BYPASS_ROLES)).containsExactly(role);
    }

    @Test
    void a_set_chain_to_a_bypassrls_role_fails() throws SQLException {
        String target = role("chain_target", "NOLOGIN BYPASSRLS");
        String middle = role("chain_middle", "NOLOGIN");
        String session = role("chain_session", "NOSUPERUSER NOBYPASSRLS");
        sql("GRANT " + target + " TO " + middle + " WITH SET TRUE");
        sql("GRANT " + middle + " TO " + session + " WITH SET TRUE");
        assertThat(as(session, TenantCatalogReader.BYPASS_ROLES)).containsExactly(target);
    }

    @Test
    void a_chain_with_a_set_false_link_does_not_fail() throws SQLException {
        String target = role("broken_target", "NOLOGIN BYPASSRLS");
        String middle = role("broken_middle", "NOLOGIN");
        String session = role("broken_session", "NOSUPERUSER NOBYPASSRLS");
        sql("GRANT " + target + " TO " + middle + " WITH SET TRUE");
        sql("GRANT " + middle + " TO " + session + " WITH SET FALSE");
        assertThat(as(session, TenantCatalogReader.BYPASS_ROLES)).isEmpty();
    }

    @Test
    void an_inherit_only_membership_does_not_fail() throws SQLException {
        String target = role("inherit_target", "NOLOGIN BYPASSRLS");
        String session = role("inherit_session", "NOSUPERUSER NOBYPASSRLS");
        sql("GRANT " + target + " TO " + session + " WITH INHERIT TRUE, SET FALSE");
        assertThat(as(session, TenantCatalogReader.BYPASS_ROLES)).isEmpty();
    }

    @Test
    void a_member_that_can_set_role_to_the_table_owner_fails() throws SQLException {
        // En el contenedor el dueño de las tablas migradas es su superusuario.
        String session = role("owner_member", "NOSUPERUSER NOBYPASSRLS");
        sql("GRANT " + containerSuperuser() + " TO " + session + " WITH SET TRUE");
        assertThat(as(session, TenantCatalogReader.OWNED_TABLES)).contains("patients", "memberships");
    }

    @Test
    void a_role_with_none_of_that_does_not_fail() throws SQLException {
        String session = role("plain", "NOSUPERUSER NOBYPASSRLS");
        assertThat(as(session, TenantCatalogReader.BYPASS_ROLES)).isEmpty();
        assertThat(as(session, TenantCatalogReader.OWNED_TABLES)).isEmpty();
    }

    @Test
    void an_object_in_another_schema_fails() throws SQLException {
        probeSchema = prefix + "schema";
        sql("CREATE SCHEMA " + probeSchema);
        sql("CREATE TABLE " + probeSchema + ".events (id BIGINT)");
        try (Connection app = appConnection()) {
            assertThat(TenantCatalogReader.strings(app, TenantCatalogReader.OTHER_SCHEMA_OBJECTS))
                    .contains(probeSchema + ".events (r)");
        }
    }
}
