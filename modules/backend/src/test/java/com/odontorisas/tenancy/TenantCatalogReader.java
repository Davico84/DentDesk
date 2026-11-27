package com.odontorisas.tenancy;

import com.odontorisas.tenancy.TenantTableClassification.FunctionInfo;
import com.odontorisas.tenancy.TenantTableClassification.RoleInfo;
import com.odontorisas.tenancy.TenantTableClassification.Snapshot;
import com.odontorisas.tenancy.TenantTableClassification.TableInfo;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Extrae del catálogo de PostgreSQL la instantánea que clasifica {@link TenantTableClassification}
 * (design D1). Lee {@code pg_class}, {@code pg_attribute}, {@code pg_policies} y {@code pg_proc}, que no
 * filtran por privilegio: {@code information_schema} ocultaría las tablas sin {@code GRANT} para el rol y
 * la prueba pasaría vacía justo donde más importa.
 */
final class TenantCatalogReader {

    /** Esquemas del sistema: {@code information_schema} y todo lo que empieza con {@code pg_}. */
    private static final String USER_SCHEMA = "n.nspname <> 'information_schema' AND n.nspname !~ '^pg_'";

    /** Funciones instaladas por una extensión (p. ej. {@code btree_gist}): no son decisiones del proyecto. */
    private static final String NOT_FROM_EXTENSION = "NOT EXISTS (SELECT 1 FROM pg_depend d "
            + "WHERE d.classid = 'pg_proc'::regclass AND d.objid = p.oid AND d.deptype = 'e')";

    static final String TABLES = """
            SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity,
                   EXISTS (SELECT 1 FROM pg_attribute a
                           WHERE a.attrelid = c.oid AND a.attname = 'organization_id'
                             AND a.attnum > 0 AND NOT a.attisdropped) AS has_org,
                   (SELECT count(*) FROM pg_policies p
                    WHERE p.schemaname = 'public' AND p.tablename = c.relname) AS policies
            FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
              AND c.relname <> 'flyway_schema_history'
            ORDER BY c.relname""";

    static final String SECURITY_DEFINERS = """
            SELECT p.proname || '(' || pg_get_function_identity_arguments(p.oid) || ')' AS signature,
                   (SELECT o.option_value FROM pg_options_to_table(p.proconfig) o
                    WHERE o.option_name = 'search_path') AS search_path
            FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname = 'public' AND p.prosecdef AND p.prokind IN ('f', 'p')
              AND %s
            ORDER BY 1""".formatted(NOT_FROM_EXTENSION);

    static final String PUBLIC_VIEWS = """
            SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relkind IN ('v', 'm')
            ORDER BY 1""";

    static final String OTHER_SCHEMA_OBJECTS = """
            SELECT n.nspname || '.' || c.relname || ' (' || c.relkind::text || ')'
            FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relkind IN ('r', 'p', 'v', 'm') AND n.nspname <> 'public' AND %1$s
            UNION ALL
            SELECT n.nspname || '.' || p.proname || '(' || pg_get_function_identity_arguments(p.oid) || ')'
            FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname <> 'public' AND %1$s AND %2$s
            ORDER BY 1""".formatted(USER_SCHEMA, NOT_FROM_EXTENSION);

    /** D7: roles con SUPERUSER o BYPASSRLS que el rol de sesión es o puede asumir con SET ROLE. */
    static final String BYPASS_ROLES = """
            SELECT r.rolname FROM pg_roles r
            WHERE (r.rolsuper OR r.rolbypassrls)
              AND pg_has_role(session_user, r.oid, 'SET')
            ORDER BY 1""";

    /** D7: tablas de public cuyo dueño es el rol de sesión o uno que puede asumir con SET ROLE. */
    static final String OWNED_TABLES = """
            SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
              AND pg_has_role(session_user, c.relowner, 'SET')
            ORDER BY 1""";

    /** Para el guardián: nombres de las funciones de public que vienen de una extensión. */
    static final String EXTENSION_FUNCTION_COUNT = """
            SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname = 'public' AND NOT (%s)""".formatted(NOT_FROM_EXTENSION);

    /** Para el guardián: funciones de public que el filtro deja pasar y se llaman como las de btree_gist. */
    static final String UNFILTERED_BTREE_GIST = """
            SELECT p.proname FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname = 'public' AND p.proname LIKE 'gbt\\_%%' AND %s""".formatted(NOT_FROM_EXTENSION);

    private TenantCatalogReader() {
    }

    static Snapshot read(Connection c) throws SQLException {
        List<TableInfo> tables = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(TABLES)) {
            while (rs.next()) {
                tables.add(new TableInfo(rs.getString(1), rs.getBoolean("has_org"), rs.getBoolean(2),
                        rs.getBoolean(3), rs.getInt("policies")));
            }
        }
        List<FunctionInfo> functions = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(SECURITY_DEFINERS)) {
            while (rs.next()) {
                functions.add(new FunctionInfo(rs.getString(1), rs.getString(2)));
            }
        }
        return new Snapshot(tables, functions, strings(c, PUBLIC_VIEWS), strings(c, OTHER_SCHEMA_OBJECTS),
                role(c));
    }

    static RoleInfo role(Connection c) throws SQLException {
        return new RoleInfo(strings(c, BYPASS_ROLES), strings(c, OWNED_TABLES));
    }

    static List<String> strings(Connection c, String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    static long count(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
