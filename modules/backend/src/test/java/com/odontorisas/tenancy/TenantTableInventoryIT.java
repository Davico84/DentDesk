package com.odontorisas.tenancy;

import com.odontorisas.AbstractRlsIntegrationTest;
import com.odontorisas.tenancy.TenantTableClassification.FunctionInfo;
import com.odontorisas.tenancy.TenantTableClassification.Snapshot;
import com.odontorisas.tenancy.TenantTableClassification.TableInfo;
import com.odontorisas.tenancy.TenantTableClassification.Violation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inventario de aislamiento contra el esquema real (update-multi-tenancy-rls-coverage, design D1): toda
 * tabla de {@code public} está clasificada en {@link TenantTableClassification#DECLARED}, las aisladas
 * tienen RLS forzada con política y toda función {@code SECURITY DEFINER} está declarada con
 * {@code search_path} fijo. Lee como el rol de aplicación ({@code NOSUPERUSER NOBYPASSRLS}), igual que
 * en producción.
 *
 * <p><b>Si falla por una tabla o función nueva:</b> clasificarla en {@code TenantTableClassification},
 * con su motivo, en el mismo cambio. No ajustar esta prueba para que pase.
 */
@SpringBootTest
class TenantTableInventoryIT extends AbstractRlsIntegrationTest {

    @Test
    void every_table_and_security_definer_function_is_classified() throws Exception {
        Snapshot snapshot;
        try (Connection app = appConnection()) {
            snapshot = TenantCatalogReader.read(app);
        }

        List<Violation> violations = TenantTableClassification.DECLARED.evaluate(snapshot);

        assertThat(violations)
                .as("Violaciones del inventario de aislamiento (clasificar en TenantTableClassification):%n%s",
                        violations.stream().map(v -> "  - " + v).collect(Collectors.joining("\n")))
                .isEmpty();
    }

    /** Guardián: una lectura vacía o filtrada por privilegios no puede pasar como verde (design D3). */
    @Test
    void the_catalog_reader_sees_what_it_must() throws Exception {
        try (Connection app = appConnection()) {
            Snapshot snapshot = TenantCatalogReader.read(app);

            assertThat(snapshot.tables()).extracting(TableInfo::name).contains("patients", "invitations");
            // clinic_capacity_facts tiene EXECUTE revocado a PUBLIC: aparece igual, pg_proc no filtra por privilegio.
            assertThat(snapshot.functions()).extracting(FunctionInfo::signature)
                    .anySatisfy(s -> assertThat(s).startsWith("current_user_is_org_admin("))
                    .anySatisfy(s -> assertThat(s).startsWith("clinic_capacity_facts("));

            // btree_gist instala funciones en public y el filtro de extensiones las deja afuera.
            assertThat(TenantCatalogReader.count(app, TenantCatalogReader.EXTENSION_FUNCTION_COUNT)).isPositive();
            assertThat(TenantCatalogReader.strings(app, TenantCatalogReader.UNFILTERED_BTREE_GIST)).isEmpty();
        }
    }
}
