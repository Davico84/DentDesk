package com.odontorisas.persistence;

import com.odontorisas.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Corre TODAS las migraciones como en Neon: el rol de Flyway es dueño de las tablas pero NO
 * superusuario (docs/deployment.md §4.2). La suite normal migra como el superusuario del contenedor,
 * que se salta incluso la RLS forzada; así una migración que tocaba datos de una tabla con FORCE ROW
 * LEVEL SECURITY pasaba en CI y fallaba en producción (deploy del 2026-10-15: el UPDATE de V23 sobre
 * memberships, sin app.user_id).
 *
 * <p>Base y rol nuevos por ejecución, sobre el contenedor compartido. Se salta sin Docker.
 */
class MigrationsAsNonSuperuserOwnerIT extends AbstractIntegrationTest {

    @Test
    void every_migration_runs_as_a_non_superuser_owner_and_still_migrates_existing_rows() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String role = "mig_owner_" + suffix;
        String password = "mig-" + suffix;
        String database = "prodlike_" + suffix;
        try (Connection su = ownerConnection(); Statement st = su.createStatement()) {
            st.execute("CREATE ROLE " + role + " LOGIN PASSWORD '" + password
                + "' NOSUPERUSER NOBYPASSRLS NOCREATEROLE NOCREATEDB");
            st.execute("CREATE DATABASE " + database + " OWNER " + role);
        }
        String url = ownerJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");

        // Hasta V22 (lo que había en producción) y con una membresía USER, como dejaba el modelo anterior.
        flyway(url, role, password, "22").migrate();
        try (Connection owner = DriverManager.getConnection(url, role, password); Statement st = owner.createStatement()) {
            st.execute("INSERT INTO users(id, email, password_hash, full_name, status) "
                + "VALUES (1, 'a@test.dev', 'x', 'A', 'ACTIVE')");
            st.execute("INSERT INTO organizations(id, name, slug, type, status) VALUES (1, 'Org', 'org', 'CLINIC', 'ACTIVE')");
            st.execute("SELECT set_config('app.user_id', '1', false)"); // la RLS forzada también vale para INSERT
            st.execute("INSERT INTO memberships(user_id, organization_id, role, status) VALUES (1, 1, 'USER', 'ACTIVE')");
        }

        var result = flyway(url, role, password, null).migrate();

        assertThat(result.success).isTrue();
        assertThat(flyway(url, role, password, null).info().pending()).isEmpty();
        try (Connection owner = DriverManager.getConnection(url, role, password); Statement st = owner.createStatement()) {
            st.execute("SELECT set_config('app.user_id', '1', false)");
            try (ResultSet rs = st.executeQuery("SELECT role FROM memberships WHERE user_id = 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).as("la migración de datos vio la fila pese a la RLS").isEqualTo("MEMBER");
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT relforcerowsecurity FROM pg_class WHERE oid = 'memberships'::regclass")) {
                rs.next();
                assertThat(rs.getBoolean(1)).as("la RLS forzada queda restaurada").isTrue();
            }
            // La rama "ADMIN gestiona su organización" (V24/V28) ya no se puede observar como dueño: desde V32 el
            // dueño lee memberships por su propia policy. Se verifica con el rol de la app en
            // ClinicCapacityFactsAsNonSuperuserOwnerIT.
        }
    }

    private static Flyway flyway(String url, String user, String password, String target) {
        var config = Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }
}
