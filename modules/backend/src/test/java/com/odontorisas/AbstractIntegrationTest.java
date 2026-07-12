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
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Flyway usa por defecto el rol admin/owner (`spring.flyway.user` en application.yml apunta
        // a ${DB_ADMIN_USERNAME:${DB_USERNAME}}). En CI no hay secrets.properties ni env, así que
        // esos placeholders quedarían sin resolver: aquí Flyway usa las credenciales del contenedor
        // (que es owner del esquema, como corresponde a las migraciones).
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        // Secret JWT dummy: application.yml lo toma de ${JWT_SECRET} (sin default) y en CI no existe.
        // Solo sirve para arrancar el contexto en tests; ningún test depende de su valor concreto.
        registry.add("app.security.jwt.secret", () -> "test-secret-ci-0123456789-0123456789-0123456789");
    }
}
