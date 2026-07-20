package com.odontorisas.branding;

import com.odontorisas.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba que el {@code CHECK} de {@code organization_branding.brand_preset} rechaza una clave
 * fuera del catálogo <b>a nivel de motor</b> — defensa en profundidad (update-organization-branding
 * design.md D2). El enum {@code BrandPreset} ya lo impide en compilación, pero eso no prueba que la
 * base de datos lo rechace si algo bypasea la capa JPA (una migración manual, otra aplicación sobre
 * la misma BD). Inserta con SQL crudo, sin pasar por Hibernate, para probar la barrera real.
 */
class OrganizationBrandingPresetCheckIT extends AbstractIntegrationTest {

    @Test
    void the_check_constraint_rejects_a_key_outside_the_catalog() throws SQLException {
        try (Connection c = ownerConnection();
             Statement s = c.createStatement()) {

            long organizationId;
            try (ResultSet rs = s.executeQuery(
                    "INSERT INTO organizations (name, slug) VALUES "
                    + "('Check Org', 'check-org-" + UUID.randomUUID() + "') RETURNING id")) {
                rs.next();
                organizationId = rs.getLong(1);
            }

            assertThatThrownBy(() -> s.execute(
                    "INSERT INTO organization_branding (organization_id, brand_preset) VALUES ("
                    + organizationId + ", 'NOT_A_PRESET')"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("violates check constraint");
        }
    }
}
