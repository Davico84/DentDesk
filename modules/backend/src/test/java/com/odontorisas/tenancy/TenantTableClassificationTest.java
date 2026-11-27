package com.odontorisas.tenancy;

import com.odontorisas.tenancy.TenantTableClassification.Definer;
import com.odontorisas.tenancy.TenantTableClassification.ClinicExemption;
import com.odontorisas.tenancy.TenantTableClassification.FunctionInfo;
import com.odontorisas.tenancy.TenantTableClassification.RoleInfo;
import com.odontorisas.tenancy.TenantTableClassification.Rule;
import com.odontorisas.tenancy.TenantTableClassification.Snapshot;
import com.odontorisas.tenancy.TenantTableClassification.TableInfo;
import com.odontorisas.tenancy.TenantTableClassification.Violation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La clasificación de tablas y funciones se ve fallar con instantáneas sintéticas, una violación por
 * regla (update-multi-tenancy-rls-coverage, design D3). Corre sin Docker: la lógica se prueba en cada
 * build aunque el inventario contra PostgreSQL real ({@code TenantTableInventoryIT}) se salte.
 *
 * <p>Las listas de estas pruebas son pequeñas y propias; las reales viven en
 * {@link TenantTableClassification#DECLARED}.
 */
class TenantTableClassificationTest {

    private static final TenantTableClassification LISTS = new TenantTableClassification(
            Map.of("patients", "negocio"),
            Map.of("memberships", "por usuario"),
            Map.of("invitations", new ClinicExemption("alta sin sesión", "ADR-0022")),
            Map.of("users", "identidad global"),
            Map.of("is_admin(org bigint)", new Definer("lee membresías", "MembershipRlsIT")));

    // --- instantánea base: cumple todas las reglas ---

    private static TableInfo isolated(String name) {
        return new TableInfo(name, true, true, true, 1);
    }

    private static List<TableInfo> okTables() {
        return new ArrayList<>(List.of(
                isolated("patients"),
                new TableInfo("memberships", true, true, true, 2),
                new TableInfo("invitations", true, false, false, 0),
                new TableInfo("users", false, false, false, 0)));
    }

    private static List<FunctionInfo> okFunctions() {
        return new ArrayList<>(List.of(new FunctionInfo("is_admin(org bigint)", "public")));
    }

    private static Snapshot snapshot(List<TableInfo> tables, List<FunctionInfo> functions) {
        return new Snapshot(tables, functions, List.of(), List.of(), RoleInfo.clean());
    }

    private static List<Violation> evaluate(Snapshot s) {
        return LISTS.evaluate(s);
    }

    private static void assertSingle(List<Violation> violations, String object, Rule rule) {
        assertThat(violations).extracting(Violation::object, Violation::rule)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(object, rule));
        assertThat(violations.get(0).toString()).contains(object).contains(rule.name());
    }

    private static List<TableInfo> replace(TableInfo table) {
        List<TableInfo> tables = okTables();
        tables.replaceAll(t -> t.name().equals(table.name()) ? table : t);
        return tables;
    }

    @Test
    void a_correct_snapshot_has_no_violations() {
        assertThat(evaluate(snapshot(okTables(), okFunctions()))).isEmpty();
    }

    // --- tablas ---

    @Test
    void an_unclassified_table_fails() {
        List<TableInfo> tables = okTables();
        tables.add(isolated("zz_probe"));
        assertSingle(evaluate(snapshot(tables, okFunctions())), "zz_probe", Rule.UNCLASSIFIED_TABLE);
    }

    @Test
    void an_obsolete_entry_fails() {
        List<TableInfo> tables = okTables();
        tables.removeIf(t -> t.name().equals("users"));
        assertSingle(evaluate(snapshot(tables, okFunctions())), "users", Rule.OBSOLETE_TABLE_ENTRY);
    }

    @Test
    void a_table_in_two_lists_fails() {
        TenantTableClassification twice = new TenantTableClassification(
                Map.of("patients", "negocio"),
                Map.of("memberships", "por usuario", "patients", "repetida"),
                Map.of("invitations", new ClinicExemption("alta sin sesión", "ADR-0022")),
                Map.of("users", "identidad global"),
                Map.of("is_admin(org bigint)", new Definer("lee membresías", "MembershipRlsIT")));
        assertSingle(twice.evaluate(snapshot(okTables(), okFunctions())), "patients", Rule.TABLE_IN_TWO_LISTS);
    }

    @Test
    void an_isolated_table_without_rls_fails() {
        // Sin RLS habilitada tampoco puede estar forzada: se informan las dos, como reglas separadas.
        List<Violation> v = evaluate(snapshot(replace(new TableInfo("patients", true, false, false, 1)), okFunctions()));
        assertThat(v).extracting(Violation::object, Violation::rule).containsExactly(
                org.assertj.core.groups.Tuple.tuple("patients", Rule.RLS_NOT_ENABLED),
                org.assertj.core.groups.Tuple.tuple("patients", Rule.RLS_NOT_FORCED));
    }

    @Test
    void an_isolated_table_with_rls_not_forced_fails() {
        assertSingle(evaluate(snapshot(replace(new TableInfo("patients", true, true, false, 1)), okFunctions())),
                "patients", Rule.RLS_NOT_FORCED);
    }

    @Test
    void an_identity_table_without_policy_fails() {
        assertSingle(evaluate(snapshot(replace(new TableInfo("memberships", true, true, true, 0)), okFunctions())),
                "memberships", Rule.NO_POLICY);
    }

    @Test
    void an_organization_table_without_organization_id_fails() {
        assertSingle(evaluate(snapshot(replace(new TableInfo("patients", false, true, true, 1)), okFunctions())),
                "patients", Rule.ORG_TABLE_WITHOUT_ORG_ID);
    }

    @Test
    void an_identity_table_does_not_need_organization_id() {
        assertThat(evaluate(snapshot(replace(new TableInfo("memberships", false, true, true, 1)), okFunctions())))
                .isEmpty();
    }

    @Test
    void an_exception_that_gained_rls_fails() {
        assertSingle(evaluate(snapshot(replace(new TableInfo("invitations", true, true, true, 1)), okFunctions())),
                "invitations", Rule.EXCEPTION_WITH_RLS);
    }

    @Test
    void an_exception_without_organization_id_fails() {
        assertSingle(evaluate(snapshot(replace(new TableInfo("invitations", false, false, false, 0)), okFunctions())),
                "invitations", Rule.EXCEPTION_WITHOUT_ORG_ID);
    }

    @Test
    void a_table_without_organization_that_gains_organization_id_fails() {
        assertSingle(evaluate(snapshot(replace(new TableInfo("users", true, false, false, 0)), okFunctions())),
                "users", Rule.NO_ORG_TABLE_WITH_ORG_ID);
    }

    // --- funciones SECURITY DEFINER ---

    @Test
    void an_undeclared_security_definer_function_fails() {
        List<FunctionInfo> functions = okFunctions();
        functions.add(new FunctionInfo("leak(org bigint)", "public"));
        assertSingle(evaluate(snapshot(okTables(), functions)), "leak(org bigint)", Rule.UNDECLARED_SECURITY_DEFINER);
    }

    @Test
    void an_undeclared_overload_fails_by_signature() {
        List<FunctionInfo> functions = okFunctions();
        functions.add(new FunctionInfo("is_admin(org bigint, user_id bigint)", "public"));
        assertSingle(evaluate(snapshot(okTables(), functions)),
                "is_admin(org bigint, user_id bigint)", Rule.UNDECLARED_SECURITY_DEFINER);
    }

    @Test
    void an_obsolete_function_entry_fails() {
        assertSingle(evaluate(snapshot(okTables(), List.of())), "is_admin(org bigint)", Rule.OBSOLETE_FUNCTION_ENTRY);
    }

    @Test
    void a_function_without_search_path_fails() {
        assertSingle(evaluate(snapshot(okTables(), List.of(new FunctionInfo("is_admin(org bigint)", null)))),
                "is_admin(org bigint)", Rule.SEARCH_PATH_NOT_FIXED);
    }

    @Test
    void a_function_with_a_loose_search_path_fails() {
        assertSingle(evaluate(snapshot(okTables(), List.of(new FunctionInfo("is_admin(org bigint)", "public, pg_temp")))),
                "is_admin(org bigint)", Rule.SEARCH_PATH_NOT_FIXED);
    }

    @Test
    void a_quoted_search_path_is_the_same_value() {
        assertThat(evaluate(snapshot(okTables(), List.of(new FunctionInfo("is_admin(org bigint)", " \"public\" ")))))
                .isEmpty();
    }

    // --- lo que el inventario no clasifica ---

    @Test
    void a_view_in_public_fails() {
        Snapshot s = new Snapshot(okTables(), okFunctions(), List.of("patients_summary"), List.of(), RoleInfo.clean());
        assertSingle(evaluate(s), "patients_summary", Rule.VIEW_IN_PUBLIC);
    }

    @Test
    void an_object_in_another_schema_fails() {
        Snapshot s = new Snapshot(okTables(), okFunctions(), List.of(), List.of("audit.events (r)"), RoleInfo.clean());
        assertSingle(evaluate(s), "audit.events (r)", Rule.OBJECT_IN_OTHER_SCHEMA);
    }

    @Test
    void a_role_that_can_assume_a_bypassrls_role_fails() {
        Snapshot s = new Snapshot(okTables(), okFunctions(), List.of(), List.of(),
                new RoleInfo(List.of("neon_superuser"), List.of()));
        assertSingle(evaluate(s), "neon_superuser", Rule.ROLE_CAN_BYPASS_RLS);
    }

    @Test
    void a_role_that_can_act_as_the_table_owner_fails() {
        Snapshot s = new Snapshot(okTables(), okFunctions(), List.of(), List.of(),
                new RoleInfo(List.of(), List.of("patients")));
        assertSingle(evaluate(s), "patients", Rule.ROLE_CAN_ACT_AS_TABLE_OWNER);
    }

    // --- las listas reales ---

    @Test
    void the_declared_lists_are_consistent_among_themselves() {
        // Ninguna tabla en dos listas y cada excepción con ADR, cada función con sus tests.
        assertThat(TenantTableClassification.DECLARED.listConflicts()).isEmpty();
        assertThat(TenantTableClassification.DECLARED.byException().values())
                .allSatisfy(e -> assertThat(e.adr()).startsWith("ADR-"));
        assertThat(TenantTableClassification.DECLARED.securityDefiners().values())
                .allSatisfy(d -> assertThat(d.coveredBy()).isNotBlank());
    }
}
