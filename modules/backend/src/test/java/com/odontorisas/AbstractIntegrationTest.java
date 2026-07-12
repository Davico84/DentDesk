package com.odontorisas;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base para tests de integración: levanta un PostgreSQL real (Testcontainers)
 * y provee los datos de conexión, garantizando paridad con producción.
 *
 * <p>Patrón <em>singleton container</em>: el contenedor se arranca una sola vez
 * (bloque estático) y se reutiliza en toda la suite; Ryuk lo limpia al terminar
 * la JVM. Deliberadamente NO se usa {@code @Container} (que arranca/destruye por
 * clase) ni {@code stop()}. Se conserva {@code @Testcontainers(disabledWithoutDocker=true)}
 * solo por su condición de ejecución: sin Docker la clase se desactiva y el bloque
 * estático nunca corre (los ITs se saltan limpios en entornos sin Docker).
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        // application.yml resuelve el datasource y Flyway desde placeholders de ENTORNO
        // (${DB_URL}, ${DB_USERNAME}, ${DB_PASSWORD}, ${DB_ADMIN_USERNAME}, ${JWT_SECRET}...).
        // En CI no existen (sin secrets.properties ni variables de entorno), así que aquí los
        // proveemos con los datos del contenedor. Rellenar los placeholders que application.yml
        // REFERENCIA (no sobreescribir spring.*) hace que TODAS las referencias resuelvan por el
        // mecanismo normal —incluida la de Flyway, que sólo funcionó con este enfoque—.
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        // Flyway (DDL) corre como rol owner (DB_ADMIN_*); en tests, el mismo usuario del
        // contenedor, que es owner del esquema. Proveerlo evita el fallback anidado a ${DB_USERNAME}.
        registry.add("DB_ADMIN_USERNAME", POSTGRES::getUsername);
        registry.add("DB_ADMIN_PASSWORD", POSTGRES::getPassword);
        // Secret JWT dummy: application.yml lo toma de ${JWT_SECRET} (sin default) y en CI no existe.
        // Solo sirve para arrancar el contexto; ningún test depende de su valor concreto.
        registry.add("JWT_SECRET", () -> "test-secret-ci-0123456789-0123456789-0123456789");
    }
}
