package com.odontorisas.persistence;

import com.odontorisas.AbstractRlsIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code clinic_capacity_facts} (V32, add-appointments design.md D4) <b>como en Neon</b>: dueño de las tablas NO
 * superusuario y la app con su propio rol. En la suite normal el dueño es superusuario y salta la RLS forzada,
 * así que un {@code SECURITY DEFINER} "funcionaría" en CI y en producción seguiría filtrado.
 *
 * <p>Verifica las dos mitades: la función le muestra a un miembro activo los datos de capacidad de los demás
 * miembros de su clínica, y lo que el rol de la app lee <b>directamente</b> de {@code memberships} y
 * {@code professional_credential} no cambió (solo lo propio; la rama ADMIN de V28 sigue viva).
 */
class ClinicCapacityFactsAsNonSuperuserOwnerIT extends AbstractRlsIntegrationTest {

    private static final long CALLER = 1;
    private static final long DENTIST = 2;
    private static final long ADMIN = 3;
    private static final long OUTSIDER = 4;

    private String url;
    private String owner;
    private String ownerPassword;

    @BeforeEach
    void migrateAsNonSuperuserOwner() throws SQLException {
        ensureAppRole(); // existe al migrar, como en producción: V6/V32 le conceden lo suyo
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = "cap_owner_" + suffix;
        ownerPassword = "cap-" + suffix;
        String database = "capfacts_" + suffix;
        try (Connection su = ownerConnection(); Statement st = su.createStatement()) {
            st.execute("CREATE ROLE " + owner + " LOGIN PASSWORD '" + ownerPassword
                + "' NOSUPERUSER NOBYPASSRLS NOCREATEROLE NOCREATEDB");
            st.execute("CREATE DATABASE " + database + " OWNER " + owner);
            st.execute("GRANT CONNECT ON DATABASE " + database + " TO " + APP_USER);
        }
        url = ownerJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
        Flyway.configure().dataSource(url, owner, ownerPassword).locations("classpath:db/migration").load().migrate();

        try (Connection c = DriverManager.getConnection(url, owner, ownerPassword); Statement st = c.createStatement()) {
            st.execute("GRANT USAGE ON SCHEMA public TO " + APP_USER);
            st.execute("INSERT INTO organizations(id, name, slug, type, status) VALUES "
                + "(1, 'Clínica', 'clinica', 'CLINIC', 'ACTIVE'), (2, 'Otra', 'otra', 'CLINIC', 'ACTIVE')");
            st.execute("INSERT INTO users(id, email, password_hash, full_name, status) VALUES "
                + "(1, 'recepcion@test.dev', 'x', 'Recepción', 'ACTIVE'), (2, 'dra@test.dev', 'x', 'Dra. Ríos', 'ACTIVE'), "
                + "(3, 'admin@test.dev', 'x', 'Admin', 'ACTIVE'), (4, 'otra@test.dev', 'x', 'Otra', 'ACTIVE')");
            // La RLS forzada vale también para el INSERT del dueño: cada fila con su identidad.
            insertMembership(st, CALLER, 1, "MEMBER");
            insertMembership(st, DENTIST, 1, "MEMBER");
            insertMembership(st, ADMIN, 1, "ADMIN");
            insertMembership(st, OUTSIDER, 2, "MEMBER");
            st.execute("SELECT set_config('app.user_id', '" + DENTIST + "', false)");
            st.execute("INSERT INTO professional_credential (user_id, cop_number, licensed_until, sworn_at) "
                + "VALUES (" + DENTIST + ", '12345', current_date + 30, now())");
        }
    }

    private static void insertMembership(Statement st, long user, long org, String role) throws SQLException {
        st.execute("SELECT set_config('app.user_id', '" + user + "', false)");
        st.execute("INSERT INTO memberships(user_id, organization_id, role, status) VALUES (" + user + ", " + org
            + ", '" + role + "', 'ACTIVE')");
    }

    /** Conexión con el rol de la app y la identidad y clínica fijadas, como las deja el binding por petición. */
    private Connection asApp(long user, long org) throws SQLException {
        Connection app = DriverManager.getConnection(url, APP_USER, APP_PWD);
        try (Statement st = app.createStatement()) {
            st.execute("SELECT set_config('app.user_id', '" + user + "', false)");
            st.execute("SELECT set_config('app.organization_id', '" + org + "', false)");
        }
        return app;
    }

    private static int count(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void an_active_member_sees_the_capacity_facts_of_the_clinic_members() throws SQLException {
        try (Connection app = asApp(CALLER, 1); Statement st = app.createStatement();
             ResultSet rs = st.executeQuery("SELECT user_id, full_name, membership_status, capacity_revoked, "
                 + "licensed_until FROM clinic_capacity_facts(" + DENTIST + ")")) {
            assertThat(rs.next()).as("la función ve la membresía ajena pese a la RLS forzada").isTrue();
            assertThat(rs.getString("full_name")).isEqualTo("Dra. Ríos");
            assertThat(rs.getString("membership_status")).isEqualTo("ACTIVE");
            assertThat(rs.getBoolean("capacity_revoked")).isFalse();
            assertThat(rs.getDate("licensed_until")).as("y la credencial ajena").isNotNull();
        }
        try (Connection app = asApp(CALLER, 1)) {
            assertThat(count(app, "SELECT count(*) FROM clinic_capacity_facts(NULL)"))
                .as("solo los miembros de la clínica del token").isEqualTo(3);
        }
    }

    @Test
    void the_app_role_still_reads_only_its_own_rows_directly() throws SQLException {
        try (Connection app = asApp(CALLER, 1)) {
            assertThat(count(app, "SELECT count(*) FROM memberships")).isEqualTo(1);
            assertThat(count(app, "SELECT count(*) FROM professional_credential")).isZero();
        }
        try (Connection app = asApp(ADMIN, 1)) {
            assertThat(count(app, "SELECT count(*) FROM memberships WHERE organization_id = 1"))
                .as("la rama ADMIN de V28 sigue viva").isEqualTo(3);
        }
    }

    /**
     * V33: la política del dueño no nombra un rol. Con {@code TO CURRENT_USER} (V32) el catálogo guardaba el rol
     * que migró, pg_dump lo escribía y una copia no se restauraba en una base sin ese rol.
     */
    @Test
    void the_owner_read_policy_names_no_role() throws SQLException {
        try (Connection c = DriverManager.getConnection(url, owner, ownerPassword); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT tablename, roles::text, qual FROM pg_policies "
                 + "WHERE schemaname = 'public' AND policyname = 'migration_owner_read' ORDER BY tablename")) {
            int found = 0;
            while (rs.next()) {
                found++;
                assertThat(rs.getString("roles")).as(rs.getString("tablename")).isEqualTo("{public}");
                assertThat(rs.getString("qual")).as(rs.getString("tablename")).doesNotContain(owner);
            }
            assertThat(found).as("memberships y professional_credential").isEqualTo(2);
        }
    }

    /**
     * V33, el ciclo real de la copia (db-backup.yml): pg_dump como el workflow, y pg_restore {@code --no-owner}
     * con un rol distinto del dueño original. El volcado no nombra al dueño en ninguna política (con V32 decía
     * {@code TO <dueño>} y la restauración abortaba donde ese rol no existe), y tras restaurar la política sigue
     * siendo "el dueño lee": el nuevo dueño ve las filas de todas las clínicas pese a la RLS forzada.
     */
    @Test
    void a_dump_restores_without_the_owner_role_and_the_new_owner_reads() throws SQLException {
        String database = url.replaceFirst("^.*/([^/?]+)(\\?.*)?$", "$1");
        String dump = "/tmp/" + database + ".dump";
        var dumped = execInDatabaseContainer("pg_dump", "-U", containerSuperuser(), "--format=custom", "--no-owner",
            "--no-acl", "--dbname=" + database, "--file=" + dump);
        assertThat(dumped.getExitCode()).as(dumped.getStderr()).isZero();

        var script = execInDatabaseContainer("pg_restore", "--no-owner", "--no-acl", "--file=-", dump);
        assertThat(script.getExitCode()).as(script.getStderr()).isZero();
        assertThat(script.getStdout()).contains("CREATE POLICY migration_owner_read");
        assertThat(script.getStdout().lines().filter(l -> l.contains("CREATE POLICY")))
            .as("ninguna política nombra al dueño de las migraciones").noneMatch(l -> l.contains(owner));

        String restorer = owner + "_r";
        String restored = database + "_r";
        try (Connection su = ownerConnection(); Statement st = su.createStatement()) {
            st.execute("CREATE ROLE " + restorer + " LOGIN PASSWORD '" + ownerPassword + "' NOSUPERUSER NOBYPASSRLS");
            st.execute("CREATE DATABASE " + restored + " OWNER " + restorer);
        }
        var restoredRun = execInDatabaseContainer("pg_restore", "-U", restorer, "--no-owner", "--no-acl",
            "--exit-on-error", "--dbname=" + restored, dump);
        assertThat(restoredRun.getExitCode()).as(restoredRun.getStderr()).isZero();

        String restoredUrl = url.replaceFirst("/[^/?]+(\\?|$)", "/" + restored + "$1");
        try (Connection c = DriverManager.getConnection(restoredUrl, restorer, ownerPassword);
             Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.user_id', '999', false)"); // nadie: solo la política del dueño
            assertThat(count(c, "SELECT count(*) FROM memberships")).as("el nuevo dueño lee todas").isEqualTo(4);
            assertThat(count(c, "SELECT count(*) FROM professional_credential")).isEqualTo(1);
        }
    }

    /** V33 es más estricta que V32: un miembro del rol dueño ya no recibe la política, solo el dueño exacto. */
    @Test
    void a_member_of_the_owner_role_does_not_get_the_owner_read_policy() throws SQLException {
        String member = owner + "_m";
        try (Connection su = ownerConnection(); Statement st = su.createStatement()) {
            st.execute("CREATE ROLE " + member + " LOGIN PASSWORD '" + ownerPassword + "' NOSUPERUSER NOBYPASSRLS");
            st.execute("GRANT " + owner + " TO " + member + " WITH INHERIT TRUE, SET FALSE");
        }
        try (Connection m = DriverManager.getConnection(url, member, ownerPassword); Statement st = m.createStatement()) {
            st.execute("SELECT set_config('app.user_id', '" + CALLER + "', false)");
            st.execute("SELECT set_config('app.organization_id', '1', false)");
            assertThat(count(m, "SELECT count(*) FROM memberships")).as("solo la propia, por la política de V28").isEqualTo(1);
        }
    }

    @Test
    void a_caller_outside_the_token_clinic_sees_nothing() throws SQLException {
        try (Connection app = asApp(OUTSIDER, 1)) {
            assertThat(count(app, "SELECT count(*) FROM clinic_capacity_facts(NULL)")).isZero();
        }
    }

    @Test
    void an_inactive_caller_sees_nothing() throws SQLException {
        try (Connection c = DriverManager.getConnection(url, owner, ownerPassword); Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.user_id', '" + CALLER + "', false)");
            st.execute("UPDATE memberships SET status = 'INACTIVE' WHERE user_id = " + CALLER);
        }
        try (Connection app = asApp(CALLER, 1)) {
            assertThat(count(app, "SELECT count(*) FROM clinic_capacity_facts(NULL)")).isZero();
        }
    }
}
