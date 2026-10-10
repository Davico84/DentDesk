package com.odontorisas;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    /** Conexión como superusuario/owner del contenedor (sembrar fixtures, DDL, o probar un CHECK). */
    protected Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /**
     * Para tests de concurrencia deterministas: espera a que otra transacción quede ESPERANDO un
     * bloqueo ({@code pg_locks} con {@code granted = false}) o a que {@code otherFinished} llegue a
     * cero, lo que pase primero. Una señal real en vez de un tiempo fijo: con el bloqueo correcto la
     * otra transacción espera; sin él, termina sin esperar (y el test debe fallar).
     */
    protected void awaitLockWaitOrFinished(CountDownLatch otherFinished) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            while (System.nanoTime() < deadline) {
                if (otherFinished.getCount() == 0) {
                    return;
                }
                try (ResultSet rs = s.executeQuery("SELECT count(*) FROM pg_locks WHERE NOT granted")) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        return;
                    }
                }
                Thread.sleep(20);
            }
            throw new IllegalStateException("La otra transacción ni terminó ni quedó esperando un bloqueo en 20 s");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        // application.yml resuelve el datasource y Flyway desde placeholders de ENTORNO
        // (${DB_URL}, ${DB_USERNAME}, ${DB_PASSWORD}, ${DB_ADMIN_USERNAME}...).
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
        // El secreto JWT, en cambio, se registra sobre la propiedad FINAL y no sobre el placeholder
        // ${JWT_SECRET} (update-jwt-secret-validation, design D2): registrar el placeholder solo fija
        // su valor, y una fuente que defina directamente app.security.jwt.secret (-D, o la variable
        // APP_SECURITY_JWT_SECRET) le ganaría a application.yml. Así la suite usa siempre su secreto.
        registry.add("app.security.jwt.secret", () -> TEST_JWT_SECRET);
    }

    /**
     * Secreto JWT solo para tests (≥ 32 bytes). Lo registra {@code @DynamicPropertySource}, que gana
     * al secrets.properties local, a {@code JWT_SECRET} del entorno y a las propiedades de sistema.
     * No gana a {@code @TestPropertySource} ni a {@code properties =} de {@code @SpringBootTest}:
     * no fijes ahí {@code app.security.jwt.secret} (docs/backend.md §11.3).
     */
    public static final String TEST_JWT_SECRET = "test-only-jwt-secret-do-not-use-in-production-0123456789";
}
