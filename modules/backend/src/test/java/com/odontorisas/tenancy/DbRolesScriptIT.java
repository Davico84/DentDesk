package com.odontorisas.tenancy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code scripts/db-roles.sql} se ejecuta de verdad con {@code psql} contra un PostgreSQL vacío
 * (add-cold-start-warmup, docs/deployment.md §4.2). Antes, sus variables de psql estaban dentro de
 * un bloque {@code DO $$ … $$}, donde psql no las reemplaza, y el script fallaba. Lo que importa
 * para el aislamiento entre clínicas: el rol de la app no es superusuario ni salta RLS.
 */
@Testcontainers(disabledWithoutDocker = true)
class DbRolesScriptIT {

    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("odontorisas");

    @BeforeAll
    static void start() {
        PG.start();
        PG.copyFileToContainer(MountableFile.forHostPath("scripts/db-roles.sql"), "/tmp/db-roles.sql");
    }

    @AfterAll
    static void stop() {
        PG.stop();
    }

    private static ExecResult runScript() throws Exception {
        return PG.execInContainer("psql", "-v", "ON_ERROR_STOP=1",
            "-v", "app_pwd=clave-app-de-prueba", "-v", "admin_pwd=clave-admin-de-prueba",
            "-U", PG.getUsername(), "-d", "odontorisas", "-f", "/tmp/db-roles.sql");
    }

    @Test
    void creates_least_privilege_roles_and_is_idempotent() throws Exception {
        ExecResult first = runScript();
        assertThat(first.getExitCode()).as(first.getStderr()).isZero();
        ExecResult second = runScript();
        assertThat(second.getExitCode()).as("segunda ejecución: " + second.getStderr()).isZero();

        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery(
                "SELECT rolsuper, rolbypassrls, rolcanlogin FROM pg_roles WHERE rolname = 'odontorisas_app'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("rolsuper")).isFalse();
                assertThat(rs.getBoolean("rolbypassrls")).isFalse();
                assertThat(rs.getBoolean("rolcanlogin")).isTrue();
            }
            try (ResultSet rs = s.executeQuery(
                "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = 'odontorisas'")) {
                rs.next();
                assertThat(rs.getString(1)).isEqualTo("odontorisas_admin");
            }
            try (ResultSet rs = s.executeQuery(
                "SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname = 'public'")) {
                rs.next();
                assertThat(rs.getString(1)).isEqualTo("odontorisas_admin");
            }
            try (ResultSet rs = s.executeQuery(
                "SELECT has_database_privilege('odontorisas_app', 'odontorisas', 'CONNECT'),"
                    + " has_schema_privilege('odontorisas_app', 'public', 'USAGE')")) {
                rs.next();
                assertThat(rs.getBoolean(1)).isTrue();
                assertThat(rs.getBoolean(2)).isTrue();
            }
        }

        // La contraseña llegó de verdad (no el literal ":'app_pwd'"): el rol puede conectarse con ella.
        try (Connection app = DriverManager.getConnection(PG.getJdbcUrl(), "odontorisas_app", "clave-app-de-prueba")) {
            assertThat(app.isValid(2)).isTrue();
        }
    }
}
