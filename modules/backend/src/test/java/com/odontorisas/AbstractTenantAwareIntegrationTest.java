package com.odontorisas;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.SQLException;

/**
 * Base para los tests que verifican el <b>enganche automático</b> del contexto de tenant.
 *
 * <p><b>El problema que resuelve.</b> {@link AbstractIntegrationTest} conecta la aplicación con
 * el usuario del contenedor, que es <b>SUPERUSER</b> — y un superusuario <b>se salta RLS por
 * completo</b> (ni {@code FORCE ROW LEVEL SECURITY} le aplica). Un test que pase por el
 * {@code EntityManager} de Spring vería <em>todas</em> las filas de <em>todas</em> las
 * organizaciones aunque el enganche funcionara: <b>no demostraría nada</b>.
 *
 * <p><b>La solución.</b> Aquí la aplicación conecta como {@code odontorisas_app}
 * ({@code NOSUPERUSER NOBYPASSRLS}), mientras Flyway sigue migrando como owner
 * ({@code DB_ADMIN_*}). <b>Es exactamente el reparto de producción</b> (ver {@code application.yml}:
 * el DDL con el rol admin, la app con un rol solo-DML no-owner), así que el test no solo prueba
 * el mecanismo: prueba el mecanismo <em>en las condiciones reales</em>.
 *
 * <p>Se sobreescribe {@code spring.datasource.username/password} —no {@code DB_USERNAME}— para no
 * pisar el {@code @DynamicPropertySource} del padre: son claves distintas y el resultado no depende
 * del orden en que Spring invoque los métodos.
 *
 * <p><b>Nota (deuda conocida):</b> el resto de la suite de integración sigue corriendo como
 * superusuario, así que sus tests <em>no</em> están sujetos a RLS. Extenderlo a toda la suite es
 * un cambio de mayor radio (GRANTs post-Flyway, ITs que hoy pasan solo porque el superusuario
 * salta RLS) y se decidió acotarlo a este change.
 */
public abstract class AbstractTenantAwareIntegrationTest extends AbstractRlsIntegrationTest {

    /**
     * El rol tiene que existir <b>antes</b> de que el pool abra su primera conexión, así que se
     * crea aquí: {@code @DynamicPropertySource} corre antes de refrescar el contexto.
     */
    @DynamicPropertySource
    static void applicationConnectsAsNonSuperuser(DynamicPropertyRegistry registry) {
        try {
            ensureAppRole();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo crear el rol de aplicación para el test", e);
        }
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PWD);
    }

    /**
     * Los {@code GRANT ... ON ALL TABLES} de {@link #ensureAppRole()} corrieron antes de Flyway,
     * cuando aún no había tablas. Ahora que el contexto está refrescado (y Flyway ha migrado),
     * se vuelven a conceder.
     */
    @BeforeEach
    void grantAppRoleOnMigratedSchema() throws SQLException {
        grantDmlOnMigratedSchema();
    }
}
