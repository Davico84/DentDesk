package com.odontorisas.tenancy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Lista única y ejecutable de cómo se aísla cada tabla de {@code public} y de qué funciones saltan la
 * RLS (update-multi-tenancy-rls-coverage, ADR-0022). Es una función pura, sin Spring ni base: recibe una
 * instantánea del catálogo y devuelve las violaciones. {@code TenantTableInventoryIT} la alimenta con el
 * catálogo real; {@code TenantTableClassificationTest}, con instantáneas sintéticas.
 *
 * <p><b>Tabla nueva ⇒ se clasifica aquí en el mismo cambio, con su motivo.</b> Función
 * {@code SECURITY DEFINER} nueva ⇒ se declara por firma con los tests que la cubren y fija
 * {@code search_path}. Es un candado de <b>clasificación</b>, no de corrección: que una política aísle
 * bien lo prueban los {@code *RlsIT} de cada capacidad (design D6).
 */
public final class TenantTableClassification {

    /** Una excepción por clínica: tiene {@code organization_id} y no tiene RLS, por la decisión del ADR. */
    public record ClinicExemption(String reason, String adr) {
    }

    /** Una función que corre con los privilegios de su dueño, con los tests que cubren su aislamiento. */
    public record Definer(String reason, String coveredBy) {
    }

    public record TableInfo(String name, boolean hasOrganizationId, boolean rlsEnabled, boolean rlsForced,
                            int policyCount) {
    }

    /** {@code searchPath} es el valor crudo de {@code proconfig} ({@code null} si la función no lo fija). */
    public record FunctionInfo(String signature, String searchPath) {
    }

    /**
     * Lo que el rol de sesión puede asumir con {@code SET ROLE} (él mismo incluido): roles con
     * {@code SUPERUSER} o {@code BYPASSRLS}, y tablas de {@code public} cuyo dueño es alcanzable.
     */
    public record RoleInfo(List<String> bypassRoles, List<String> ownedTables) {
        public static RoleInfo clean() {
            return new RoleInfo(List.of(), List.of());
        }
    }

    public record Snapshot(List<TableInfo> tables, List<FunctionInfo> functions, List<String> publicViews,
                           List<String> otherSchemaObjects, RoleInfo role) {
    }

    /** Numeradas como en el design D2. */
    public enum Rule {
        UNCLASSIFIED_TABLE("tabla sin clasificar (1)"),
        OBSOLETE_TABLE_ENTRY("entrada de tabla que ya no existe (2)"),
        TABLE_IN_TWO_LISTS("tabla en dos listas (3)"),
        RLS_NOT_ENABLED("tabla aislada sin RLS habilitada (4)"),
        RLS_NOT_FORCED("tabla aislada sin RLS forzada (4)"),
        NO_POLICY("tabla aislada sin política (4)"),
        ORG_TABLE_WITHOUT_ORG_ID("aislada por organización sin organization_id (5)"),
        EXCEPTION_WITH_RLS("excepción por clínica con RLS: moverla a la lista de aisladas (6)"),
        EXCEPTION_WITHOUT_ORG_ID("excepción por clínica sin organization_id: mal clasificada (7)"),
        NO_ORG_TABLE_WITH_ORG_ID("tabla sin organización que tiene organization_id (8)"),
        UNDECLARED_SECURITY_DEFINER("función SECURITY DEFINER sin declarar (9)"),
        OBSOLETE_FUNCTION_ENTRY("función declarada que ya no existe o no es SECURITY DEFINER (10)"),
        SEARCH_PATH_NOT_FIXED("SECURITY DEFINER sin search_path fijado a un valor permitido (11)"),
        VIEW_IN_PUBLIC("vista en public: decidir su aislamiento en su propio cambio (12)"),
        OBJECT_IN_OTHER_SCHEMA("objeto en un esquema de usuario distinto de public (13)"),
        ROLE_CAN_BYPASS_RLS("el rol de sesión es o puede asumir un rol con SUPERUSER o BYPASSRLS (14)"),
        ROLE_CAN_ACT_AS_TABLE_OWNER("el rol de sesión es o puede asumir al dueño de la tabla (15)");

        private final String description;

        Rule(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    public record Violation(String object, Rule rule) {
        @Override
        public String toString() {
            return object + ": " + rule.name() + " — " + rule.description();
        }
    }

    /**
     * Valores permitidos de {@code search_path}, como lista ordenada ya normalizada. Ampliarla es una
     * edición visible en el diff (design D2, regla 11).
     */
    static final Set<List<String>> ALLOWED_SEARCH_PATHS = Set.of(List.of("public"));

    /** Las listas reales. Motivos y referencias: ADR-0022. */
    public static final TenantTableClassification DECLARED = new TenantTableClassification(
            // 1. Aisladas por organización: organization_id + @TenantId + RLS forzada con política.
            ordered(
                    "patients", "ficha del paciente de la clínica",
                    "historia_clinica", "historia clínica del paciente",
                    "clinical_history_diagnosis", "diagnósticos de la historia clínica",
                    "clinical_background_entry", "antecedentes de la historia clínica",
                    "consultation_note", "nota de consulta",
                    "consultation_note_correction", "correcciones de una nota sellada",
                    "consultation_note_diagnosis", "diagnósticos de la nota de consulta",
                    "consultation_note_finding", "hallazgos de la nota de consulta",
                    "dental_chart_block", "bloques del odontograma",
                    "dental_chart_finding", "hallazgos del odontograma",
                    "dental_chart_finding_tooth", "piezas de un hallazgo del odontograma",
                    "appointments", "agenda de la clínica"),
            // 2. Aisladas por identidad: RLS forzada con política por usuario.
            ordered(
                    "memberships", "pertenencia del usuario: se aísla por user_id (y ADMIN de la clínica, V28)",
                    "professional_credential", "credencial del profesional, sin organization_id: por user_id o "
                            + "ADMIN de la clínica del miembro (V30)"),
            // 3. Excepciones por clínica: organization_id sin RLS, cada una decidida por un ADR.
            orderedExemptions(
                    "organization_settings", new ClinicExemption(
                            "la PK es el organization_id; se lee por la del token o la crea la plataforma",
                            "ADR-0003"),
                    "organization_branding", new ClinicExemption(
                            "la PK es el organization_id; se lee por la del token", "ADR-0003"),
                    "invitations", new ClinicExemption(
                            "la aceptación llega por hash de token, sin sesión ni organización en el contexto; "
                                    + "aislada por código (consulta por organización y comparación posterior)",
                            "ADR-0022")),
            // 4. Sin organization_id: tenant raíz, identidad global y catálogos.
            ordered(
                    "organizations", "el tenant raíz",
                    "users", "identidad global: un usuario puede pertenecer a varias clínicas",
                    "refresh_tokens", "sesiones del usuario global",
                    "password_reset_tokens", "recuperación de la cuenta del usuario global",
                    "platform_admins", "operadores de la plataforma, por encima de las clínicas",
                    "cie10_code", "catálogo CIE-10 global",
                    "dental_finding_catalog", "catálogo de hallazgos del odontograma (NTS 188)",
                    "app_metadata", "metadatos de la aplicación"),
            // 5. Funciones SECURITY DEFINER de public (sin las de extensiones), por firma.
            orderedDefiners(
                    "current_user_is_org_admin(target_org_id bigint)", new Definer(
                            "¿el usuario del token es ADMIN de esa organización? Lo usan las políticas de "
                                    + "memberships (V28) y professional_credential (V30) y el trigger de revocación",
                            "MembershipRlsIT, ProfessionalCredentialRlsIT"),
                    "clinic_capacity_facts(target_user_id bigint)", new Definer(
                            "capacidad clínica de un miembro de la clínica activa, para agendar con otro "
                                    + "odontólogo; estricta con app.organization_id y exige miembro ACTIVE (V32)",
                            "ClinicCapacityFactsAsNonSuperuserOwnerIT, AppointmentServiceIT")));

    private final Map<String, String> byOrganization;
    private final Map<String, String> byIdentity;
    private final Map<String, ClinicExemption> byException;
    private final Map<String, String> withoutOrganization;
    private final Map<String, Definer> securityDefiners;

    public TenantTableClassification(Map<String, String> byOrganization, Map<String, String> byIdentity,
                                     Map<String, ClinicExemption> byException,
                                     Map<String, String> withoutOrganization,
                                     Map<String, Definer> securityDefiners) {
        this.byOrganization = Map.copyOf(byOrganization);
        this.byIdentity = Map.copyOf(byIdentity);
        this.byException = Map.copyOf(byException);
        this.withoutOrganization = Map.copyOf(withoutOrganization);
        this.securityDefiners = Map.copyOf(securityDefiners);
    }

    public Map<String, ClinicExemption> byException() {
        return byException;
    }

    public Map<String, Definer> securityDefiners() {
        return securityDefiners;
    }

    /** Tablas declaradas en más de una lista (regla 3), sin mirar el catálogo. */
    public List<Violation> listConflicts() {
        List<Violation> out = new ArrayList<>();
        for (String table : declaredTables()) {
            int lists = (byOrganization.containsKey(table) ? 1 : 0) + (byIdentity.containsKey(table) ? 1 : 0)
                    + (byException.containsKey(table) ? 1 : 0) + (withoutOrganization.containsKey(table) ? 1 : 0);
            if (lists > 1) {
                out.add(new Violation(table, Rule.TABLE_IN_TWO_LISTS));
            }
        }
        return out;
    }

    /** Todas las violaciones, en orden estable (por regla y por objeto); vacía si el catálogo cumple. */
    public List<Violation> evaluate(Snapshot snapshot) {
        List<Violation> out = new ArrayList<>(listConflicts());
        Map<String, TableInfo> tables = snapshot.tables().stream()
                .collect(Collectors.toMap(TableInfo::name, t -> t, (a, b) -> a, LinkedHashMap::new));

        for (TableInfo t : tables.values()) {
            String n = t.name();
            if (!declaredTables().contains(n)) {
                out.add(new Violation(n, Rule.UNCLASSIFIED_TABLE));
                continue;
            }
            if (byOrganization.containsKey(n) || byIdentity.containsKey(n)) {
                if (!t.rlsEnabled()) {
                    out.add(new Violation(n, Rule.RLS_NOT_ENABLED));
                }
                if (!t.rlsForced()) {
                    out.add(new Violation(n, Rule.RLS_NOT_FORCED));
                }
                if (t.policyCount() == 0) {
                    out.add(new Violation(n, Rule.NO_POLICY));
                }
            }
            if (byOrganization.containsKey(n) && !t.hasOrganizationId()) {
                out.add(new Violation(n, Rule.ORG_TABLE_WITHOUT_ORG_ID));
            }
            if (byException.containsKey(n)) {
                if (t.rlsEnabled()) {
                    out.add(new Violation(n, Rule.EXCEPTION_WITH_RLS));
                }
                if (!t.hasOrganizationId()) {
                    out.add(new Violation(n, Rule.EXCEPTION_WITHOUT_ORG_ID));
                }
            }
            if (withoutOrganization.containsKey(n) && t.hasOrganizationId()) {
                out.add(new Violation(n, Rule.NO_ORG_TABLE_WITH_ORG_ID));
            }
        }
        for (String declared : declaredTables()) {
            if (!tables.containsKey(declared)) {
                out.add(new Violation(declared, Rule.OBSOLETE_TABLE_ENTRY));
            }
        }

        Set<String> present = new TreeSet<>();
        for (FunctionInfo f : snapshot.functions()) {
            present.add(f.signature());
            if (!securityDefiners.containsKey(f.signature())) {
                out.add(new Violation(f.signature(), Rule.UNDECLARED_SECURITY_DEFINER));
            }
            if (!ALLOWED_SEARCH_PATHS.contains(normalizeSearchPath(f.searchPath()))) {
                out.add(new Violation(f.signature(), Rule.SEARCH_PATH_NOT_FIXED));
            }
        }
        for (String declared : new TreeSet<>(securityDefiners.keySet())) {
            if (!present.contains(declared)) {
                out.add(new Violation(declared, Rule.OBSOLETE_FUNCTION_ENTRY));
            }
        }

        snapshot.publicViews().forEach(v -> out.add(new Violation(v, Rule.VIEW_IN_PUBLIC)));
        snapshot.otherSchemaObjects().forEach(o -> out.add(new Violation(o, Rule.OBJECT_IN_OTHER_SCHEMA)));
        snapshot.role().bypassRoles().forEach(r -> out.add(new Violation(r, Rule.ROLE_CAN_BYPASS_RLS)));
        snapshot.role().ownedTables().forEach(t -> out.add(new Violation(t, Rule.ROLE_CAN_ACT_AS_TABLE_OWNER)));

        out.sort((a, b) -> a.rule() != b.rule()
                ? a.rule().compareTo(b.rule())
                : a.object().compareTo(b.object()));
        return List.copyOf(out);
    }

    /**
     * {@code "public"}, {@code public} y {@code  public } son el mismo valor: se separa por comas y se
     * quitan espacios y comillas dobles. Sin valor ⇒ lista vacía, que nunca está permitida.
     */
    static List<String> normalizeSearchPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(s -> s.trim().replace("\"", "").trim())
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private Set<String> declaredTables() {
        Set<String> all = new TreeSet<>();
        all.addAll(byOrganization.keySet());
        all.addAll(byIdentity.keySet());
        all.addAll(byException.keySet());
        all.addAll(withoutOrganization.keySet());
        return all;
    }

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            putOnce(m, pairs[i], pairs[i + 1]);
        }
        return m;
    }

    private static Map<String, ClinicExemption> orderedExemptions(Object... pairs) {
        Map<String, ClinicExemption> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            putOnce(m, (String) pairs[i], (ClinicExemption) pairs[i + 1]);
        }
        return m;
    }

    private static Map<String, Definer> orderedDefiners(Object... pairs) {
        Map<String, Definer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            putOnce(m, (String) pairs[i], (Definer) pairs[i + 1]);
        }
        return m;
    }

    private static <V> void putOnce(Map<String, V> m, String key, V value) {
        if (m.put(Objects.requireNonNull(key), Objects.requireNonNull(value)) != null) {
            throw new IllegalArgumentException("entrada repetida en la misma lista: " + key);
        }
    }
}
