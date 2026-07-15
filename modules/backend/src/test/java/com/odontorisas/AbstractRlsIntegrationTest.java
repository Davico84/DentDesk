package com.odontorisas;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base para tests de Row-Level Security. Reutiliza el PostgreSQL de {@link AbstractIntegrationTest}
 * (donde Flyway ya aplicó el esquema como superusuario/owner) y añade un rol de aplicación
 * <b>no-owner, no-superuser</b> (como en producción): es el único contexto en el que RLS aplica de
 * verdad (el superusuario y el owner la saltan). Las aserciones de aislamiento se hacen con una
 * conexión JDBC directa como ese rol.
 */
@org.testcontainers.junit.jupiter.Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractRlsIntegrationTest extends AbstractIntegrationTest {

    protected static final String APP_USER = "odontorisas_app";
    protected static final String APP_PWD = "app_pwd";

    private static volatile boolean prepared = false;

    /** Conexión como superusuario/owner (para sembrar datos y DDL de fixtures; salta RLS). */
    protected Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** Conexión como rol de aplicación (no-owner, no-superuser) — sujeto a RLS. */
    protected Connection appConnection() throws SQLException {
        ensureAppRole();
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), APP_USER, APP_PWD);
    }

    /** Crea el rol de app (idempotente) y le concede DML sobre el esquema ya migrado. */
    protected static synchronized void ensureAppRole() throws SQLException {
        if (prepared) {
            return;
        }
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement s = c.createStatement()) {
            s.execute("DO $$ BEGIN "
                + "IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + APP_USER + "') THEN "
                + "CREATE ROLE " + APP_USER + " LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD '" + APP_PWD + "'; "
                + "END IF; END $$;");
            s.execute("GRANT USAGE ON SCHEMA public TO " + APP_USER);
        }
        prepared = true;
        grantDmlOnMigratedSchema();
    }

    /**
     * Concede DML sobre las tablas ya existentes. Es idempotente y se puede reinvocar: los
     * {@code GRANT ... ON ALL TABLES} solo alcanzan a las tablas que existen <b>en ese momento</b>,
     * así que si el rol se crea antes de que Flyway migre (lo que hace
     * {@code AbstractTenantAwareIntegrationTest}, porque el pool necesita el rol para conectar),
     * hay que volver a concederlos <b>después</b> de la migración.
     */
    protected static void grantDmlOnMigratedSchema() throws SQLException {
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement s = c.createStatement()) {
            s.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO " + APP_USER);
            s.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO " + APP_USER);
        }
    }
}
