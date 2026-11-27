package com.odontorisas.tenancy;

import com.odontorisas.AbstractRlsIntegrationTest;
import com.odontorisas.common.IdentityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifica el mecanismo de autoría <b>end-to-end</b>: que declarar {@code @CreatedBy}/
 * {@code @LastModifiedBy} baste para que se rellenen solos, sin que el código de negocio escriba
 * una línea — mismo espíritu que {@link AutomaticTenantBindingIT} para el tenant.
 *
 * <p>A diferencia de aquel, esto no es sobre RLS: no hace falta el rol de aplicación no-superusuario,
 * así que basta con {@link AbstractRlsIntegrationTest} (para reutilizar {@code ownerConnection()}
 * al crear la tabla-fixture, que no está en Flyway).
 */
@SpringBootTest
class AutomaticAuditingIT extends AbstractRlsIntegrationTest {

    private static final long USER_A = 10L;
    private static final long USER_B = 20L;

    @Autowired
    private AuditFixtureRepository repository;

    @BeforeEach
    void createFixtureTable() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("DROP TABLE IF EXISTS test_audit_fixture");
            s.execute("CREATE TABLE test_audit_fixture (id BIGSERIAL PRIMARY KEY, "
                + "label TEXT, created_by BIGINT, updated_by BIGINT)");
        }
    }

    @AfterEach
    void clearContext() {
        IdentityContext.clear();
    }

    /** Sin esto la tabla queda en el esquema compartido y {@code TenantTableInventoryIT} la ve sin clasificar. */
    @AfterEach
    void dropTestAuditFixture() throws SQLException {
        try (Connection su = ownerConnection(); Statement s = su.createStatement()) {
            s.execute("DROP TABLE IF EXISTS test_audit_fixture");
        }
    }

    @Test
    void records_who_created_and_who_last_modified() {
        IdentityContext.set(USER_A);
        AuditFixtureEntity saved = repository.save(new AuditFixtureEntity("first"));

        assertThat(saved.getCreatedBy()).isEqualTo(USER_A);
        assertThat(saved.getUpdatedBy()).isEqualTo(USER_A);

        IdentityContext.clear();
        IdentityContext.set(USER_B);
        saved.setLabel("edited");
        AuditFixtureEntity updated = repository.saveAndFlush(saved);

        assertThat(updated.getCreatedBy())
            .as("el autor de la creación no cambia con una edición posterior")
            .isEqualTo(USER_A);
        assertThat(updated.getUpdatedBy())
            .as("el autor de la última modificación sí cambia")
            .isEqualTo(USER_B);
    }

    @Test
    void saving_without_identity_context_does_not_throw_and_leaves_authorship_unset() {
        // Simula un flujo previo a la sesión (p. ej. el alta de la primera organización): no hay
        // IdentityContext. La autoría debe quedar sin asignar, no bloquear la operación.
        assertThatCode(() -> repository.save(new AuditFixtureEntity("sin-contexto")))
            .doesNotThrowAnyException();

        AuditFixtureEntity saved = repository.findAll().get(0);
        assertThat(saved.getCreatedBy()).isNull();
        assertThat(saved.getUpdatedBy()).isNull();
    }
}
