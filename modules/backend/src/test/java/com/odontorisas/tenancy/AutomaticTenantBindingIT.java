package com.odontorisas.tenancy;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.service.auth.AuthResult;
import com.odontorisas.service.auth.AuthService;
import com.odontorisas.service.auth.RegisterCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica el <b>enganche automático</b> del contexto de tenant: que el aislamiento sea
 * <b>por defecto, no por disciplina</b>.
 *
 * <p>La diferencia con {@code OrganizationRlsIT} es la que da valor a este test.
 * Aquél prueba la <em>política de PostgreSQL</em> abriendo conexiones JDBC directas y fijando
 * él mismo {@code app.organization_id}. Este prueba el <em>mecanismo de la aplicación</em>:
 * entra por una operación {@code @Transactional} normal, con el contexto poblado <b>como lo
 * haría el filtro JWT</b>, y <b>no llama a {@code TenantSession.bind(...)} en ningún momento</b>.
 *
 * <p><b>Esa ausencia es el aserto.</b> Si el enganche no existiera, o fijara la variable sobre
 * la conexión equivocada, RLS no encontraría {@code app.organization_id} y estos tests fallarían.
 *
 * <p>Corre como el rol de aplicación <b>no-superusuario</b> (ver
 * {@link AbstractTenantAwareIntegrationTest}): con el superusuario del contenedor, RLS se
 * saltaría y el test pasaría en verde con el mecanismo roto.
 */
@SpringBootTest
@Import(AutomaticTenantBindingIT.Fixture.class)
class AutomaticTenantBindingIT extends AbstractTenantAwareIntegrationTest {

    private static final long ORG_A = 1L;
    private static final long ORG_B = 2L;
    private static final long SOME_USER = 42L;

    @TestConfiguration
    static class Fixture {
        @Bean
        TestBusinessAccess testBusinessAccess() {
            return new TestBusinessAccess();
        }
    }

    @Autowired
    private TestBusinessAccess businessAccess;

    @Autowired
    private AuthService authService;

    /** Tabla de negocio de prueba con la convención de aislamiento. La crea el owner. */
    @BeforeEach
    void createBusinessFixtureTable() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("DROP TABLE IF EXISTS test_business");
            s.execute("CREATE TABLE test_business (id BIGSERIAL PRIMARY KEY, "
                + "organization_id BIGINT NOT NULL, label TEXT)");
            s.execute("ALTER TABLE test_business ENABLE ROW LEVEL SECURITY");
            s.execute("ALTER TABLE test_business FORCE ROW LEVEL SECURITY");
            s.execute("CREATE POLICY organization_isolation ON test_business "
                + "USING (organization_id = current_setting('app.organization_id')::bigint)");
            s.execute("INSERT INTO test_business(organization_id, label) VALUES "
                + "(" + ORG_A + ", 'a1'), (" + ORG_A + ", 'a2'), (" + ORG_B + ", 'b1')");
            s.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON test_business TO " + APP_USER);
            s.execute("GRANT USAGE, SELECT ON test_business_id_seq TO " + APP_USER);
        }
    }

    @AfterEach
    void clearContext() {
        IdentityContext.clear();
        OrganizationContext.clear();
    }

    /** Puebla el contexto igual que {@code JwtAuthenticationFilter} tras verificar el token. */
    private void asRequestOf(long userId, long organizationId) {
        IdentityContext.set(userId);
        OrganizationContext.set(organizationId);
    }

    @Test
    void a_business_entity_without_isolation_code_is_still_isolated() {
        asRequestOf(SOME_USER, ORG_A);
        assertThat(businessAccess.visibleLabels()).containsExactlyInAnyOrder("a1", "a2");

        clearContext();
        asRequestOf(SOME_USER, ORG_B);
        assertThat(businessAccess.visibleLabels()).containsExactly("b1");
    }

    @Test
    void an_organization_cannot_read_another_ones_data() {
        asRequestOf(SOME_USER, ORG_A);
        assertThat(businessAccess.visibleLabels()).doesNotContain("b1");
    }

    @Test
    void an_operation_without_context_fails_loudly_instead_of_returning_empty() {
        // Sin IdentityContext ni OrganizationContext: el enganche no fija nada, y la política
        // —declarada sin missing_ok— hace que current_setting('app.organization_id') lance.
        assertThatThrownBy(() -> businessAccess.visibleLabels())
            .as("una consulta de negocio sin contexto debe FALLAR, no devolver una lista vacía "
                + "(un vacío se confunde con «esta clínica no tiene datos» y oculta el fallo)")
            .isInstanceOf(Exception.class);
    }

    @Test
    void the_pre_session_flows_still_work() {
        // El registro ocurre ANTES de que exista contexto: la identidad y su membresía se crean en
        // esa misma transacción. El enganche no debe bloquearlo por falta de contexto.
        String email = "admin-" + UUID.randomUUID() + "@clinica.test";
        String token = seedVerifiedInvitation("Clínica Nueva", email, "ADMIN");
        AuthResult result = authService.register(
            new RegisterCommand(token, email, "password-segura", "Ada Lovelace"));

        assertThat(result).isNotNull();
    }
}
