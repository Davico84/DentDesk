package com.odontorisas.auth;

import com.odontorisas.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenarios de la capacidad authentication a nivel HTTP (contra PostgreSQL real
 * vía Testcontainers). Se salta sin Docker; corre en CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIT extends AbstractIntegrationTest {

    private static final String REFRESH_COOKIE = "refresh_token";

    @Autowired
    MockMvc mockMvc;

    /** Registro con invitación ADMIN ya verificada (cada llamada siembra su propia organización). */
    private String registerBody(String email, String password) {
        return invitedAdminRegisterBody("Clínica Test", email, password, "Ana Pérez");
    }

    private static String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void register_success_returns_201_with_admin_membership_and_active_organization() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("nuevo@clinica.test", "password123")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.email").value("nuevo@clinica.test"))
            .andExpect(jsonPath("$.user.role").value("ADMIN"))
            .andExpect(jsonPath("$.user.organizationName").value("Clínica Test"))
            .andExpect(jsonPath("$.user.organizationSlug").isNotEmpty())
            .andExpect(cookie().exists(REFRESH_COOKIE))
            .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Lax")));
    }

    @Test
    void register_duplicate_email_returns_409() throws Exception {
        String body = registerBody("dup@clinica.test", "password123");
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void register_short_password_returns_400() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("corta@clinica.test", "1234")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors[?(@.field == 'password')]").isNotEmpty());
    }

    @Test
    void login_valid_credentials_returns_200() throws Exception {
        String email = "login@clinica.test";
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "password123")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(cookie().exists(REFRESH_COOKIE));
    }

    /**
     * Scenario "Un error de negocio usa el formato común": este 401 (credenciales inválidas) ya
     * era RFC 9457 —nace de una excepción de negocio—, a diferencia del de sesión ausente. Se fija
     * por test para que siga siéndolo: ambos 401 deben compartir forma.
     */
    @Test
    void login_invalid_credentials_returns_401_generic_as_problem_detail() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("noexiste@clinica.test", "password123")))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.timestamp").isNotEmpty())
            .andExpect(jsonPath("$.detail").value("Correo electrónico o contraseña incorrectos"));
    }

    @Test
    void login_missing_fields_returns_400() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_with_valid_cookie_returns_200_and_rotates() throws Exception {
        MvcResult registered = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("refresh@clinica.test", "password123")))
            .andExpect(status().isCreated())
            .andReturn();
        Cookie refresh = registered.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(refresh).isNotNull();

        mockMvc.perform(post("/auth/refresh").cookie(refresh))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(cookie().exists(REFRESH_COOKIE));
    }

    @Test
    void refresh_without_cookie_returns_401() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_returns_204_and_clears_cookie() throws Exception {
        MvcResult registered = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("logout@clinica.test", "password123")))
            .andExpect(status().isCreated())
            .andReturn();
        Cookie refresh = registered.getResponse().getCookie(REFRESH_COOKIE);

        mockMvc.perform(post("/auth/logout").cookie(refresh))
            .andExpect(status().isNoContent())
            .andExpect(cookie().maxAge(REFRESH_COOKIE, 0));
    }

    /**
     * Scenario "El error de sesión ausente usa el formato común": antes el entry point hacía
     * {@code sendError(401)} y devolvía el cuerpo por defecto del contenedor
     * ({@code {timestamp,status,error,path}}). Ahora delega en el HandlerExceptionResolver, así
     * que sale en RFC 9457 como el resto — requisito para describir los errores con un único
     * schema en el contrato.
     */
    @Test
    void protected_route_without_token_returns_401_as_problem_detail() throws Exception {
        mockMvc.perform(get("/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("/errors/unauthorized"))
            .andExpect(jsonPath("$.title").isNotEmpty())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").isNotEmpty())
            .andExpect(jsonPath("$.timestamp").isNotEmpty())
            // El cuerpo por defecto del contenedor traía estas claves; ya no deben aparecer.
            .andExpect(jsonPath("$.error").doesNotExist())
            .andExpect(jsonPath("$.path").doesNotExist());
    }

    // --- Bloqueo por intentos fallidos (update-authentication) ---
    // Umbral/ventana de application.yml (defaults): 5 intentos, PT15M.

    private static final int MAX_ATTEMPTS = 5;

    private void failLogin(String email, String wrongPassword) throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, wrongPassword)))
            .andExpect(status().isUnauthorized());
    }

    private int failedAttempts(String email) throws Exception {
        try (Connection c = ownerConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT failed_login_attempts FROM users WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private Timestamp lockedUntilOf(String email) throws Exception {
        try (Connection c = ownerConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT locked_until FROM users WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getTimestamp(1);
            }
        }
    }

    private String statusOf(String email) throws Exception {
        try (Connection c = ownerConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT status FROM users WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private long countUsersWithEmail(String email) throws Exception {
        try (Connection c = ownerConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT count(*) FROM users WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Simula el paso del tiempo (D11): mueve locked_until directamente, sin tocar el reloj de la app. */
    private void expireLockDirectly(String email) throws Exception {
        try (Connection c = ownerConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE users SET locked_until = ? WHERE email = ?")) {
            ps.setTimestamp(1, Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
            ps.setString(2, email);
            ps.executeUpdate();
        }
    }

    private void setStatusDirectly(String email, String status) throws Exception {
        try (Connection c = ownerConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE users SET status = ? WHERE email = ?")) {
            ps.setString(1, status);
            ps.setString(2, email);
            ps.executeUpdate();
        }
    }

    @Test
    void register_leaves_the_new_identity_with_no_lockout_state() throws Exception {
        String email = "sinbloqueo@clinica.test";
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
            .andExpect(status().isCreated());

        assertThat(failedAttempts(email)).isZero();
        assertThat(lockedUntilOf(email)).isNull();
    }

    @Test
    void login_failed_attempt_persists_even_though_the_login_itself_is_rejected() throws Exception {
        String email = "persiste@clinica.test";
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
            .andExpect(status().isCreated());

        // El incremento vive en su propia transacción (LoginAttemptService, REQUIRES_NEW): debe
        // sobrevivir aunque login() lance el 401 y su propia transacción se revierta (D7).
        failLogin(email, "mala");

        assertThat(failedAttempts(email)).isEqualTo(1);
    }

    @Test
    void login_concurrent_failed_attempts_all_count_without_losing_updates() throws Exception {
        String email = "concurrencia@clinica.test";
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
            .andExpect(status().isCreated());

        int concurrentAttempts = 4; // < MAX_ATTEMPTS, para no cruzar el umbral y solo medir el conteo
        ExecutorService pool = Executors.newFixedThreadPool(concurrentAttempts);
        CountDownLatch ready = new CountDownLatch(concurrentAttempts);
        CountDownLatch start = new CountDownLatch(1);
        try {
            for (int i = 0; i < concurrentAttempts; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        failLogin(email, "mala");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            ready.await();
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        // El UPDATE atómico (failed_login_attempts + 1 en la propia sentencia) hace que Postgres
        // serialice los UPDATE concurrentes: N fallos simultáneos cuentan N, no menos (D7).
        assertThat(failedAttempts(email)).isEqualTo(concurrentAttempts);
    }

    @Test
    void login_locks_the_account_after_reaching_the_attempt_threshold() throws Exception {
        String email = "bloqueo@clinica.test";
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
            .andExpect(status().isCreated());

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            failLogin(email, "mala");
        }

        assertThat(failedAttempts(email)).isEqualTo(MAX_ATTEMPTS);
        assertThat(lockedUntilOf(email)).isNotNull();
    }

    /**
     * Cubre a la vez "cuenta bloqueada con credenciales correctas" y la garantía anti-enumeración
     * a nivel de respuesta: el rechazo por bloqueo reutiliza la MISMA excepción que credenciales
     * inválidas, así que el shape (status/type/detail) es idéntico por construcción, no por
     * código paralelo que podría divergir.
     */
    @Test
    void login_locked_account_rejects_even_correct_credentials_identically_to_invalid_credentials() throws Exception {
        String email = "bloqueada@clinica.test";
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
            .andExpect(status().isCreated());
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            failLogin(email, "mala");
        }

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "password123"))) // credenciales CORRECTAS
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").value("Correo electrónico o contraseña incorrectos"));
    }

    @Test
    void login_insisting_during_lockout_does_not_extend_the_window_or_the_counter() throws Exception {
        String email = "insiste@clinica.test";
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
            .andExpect(status().isCreated());
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            failLogin(email, "mala");
        }
        Timestamp lockedAt = lockedUntilOf(email);
        int attemptsAtLock = failedAttempts(email);

        failLogin(email, "otra-mala"); // insiste ya bloqueada

        // D2/D8: rechazada ANTES de tocar el contador — ni suma ni retrasa la expiración.
        assertThat(failedAttempts(email)).isEqualTo(attemptsAtLock);
        assertThat(lockedUntilOf(email)).isEqualTo(lockedAt);
    }

    @Test
    void login_state_rejection_leaves_the_accumulated_counter_untouched() throws Exception {
        String email = "rechazoestado@clinica.test";
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
            .andExpect(status().isCreated());
        failLogin(email, "mala");
        failLogin(email, "mala"); // contador acumulado en 2, sin llegar al umbral
        setStatusDirectly(email, "DISABLED");

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "password123"))) // credenciales CORRECTAS
            .andExpect(status().isUnauthorized());

        // D7/D8: el rechazo por estado no resetea (el reset vive en la misma transacción que
        // activeMembership, y esta la corta antes) ni incrementa el contador acumulado.
        assertThat(failedAttempts(email)).isEqualTo(2);
        assertThat(statusOf(email)).isEqualTo("DISABLED");
    }

    @Test
    void login_after_the_lock_expires_with_correct_password_succeeds_and_resets() throws Exception {
        String email = "expira-ok@clinica.test";
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
            .andExpect(status().isCreated());
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            failLogin(email, "mala");
        }
        expireLockDirectly(email);

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "password123")))
            .andExpect(status().isOk());

        assertThat(failedAttempts(email)).isZero();
        assertThat(lockedUntilOf(email)).isNull();
    }

    @Test
    void login_after_the_lock_expires_with_wrong_password_does_not_relock_immediately() throws Exception {
        String email = "expira-mal@clinica.test";
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
            .andExpect(status().isCreated());
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            failLogin(email, "mala");
        }
        expireLockDirectly(email);

        failLogin(email, "sigue-mala");

        // D3: el conteo parte de cero tras expirar — un único fallo posterior no vuelve a
        // bloquear de inmediato (queda en 1, no en MAX_ATTEMPTS + 1, y sin lockedUntil).
        assertThat(failedAttempts(email)).isEqualTo(1);
        assertThat(lockedUntilOf(email)).isNull();
    }

    @Test
    void login_repeated_attempts_against_an_unknown_email_create_no_state() throws Exception {
        String ghost = "fantasma@clinica.test";

        failLogin(ghost, "cualquiera");
        failLogin(ghost, "cualquiera");
        failLogin(ghost, "cualquiera");

        assertThat(countUsersWithEmail(ghost)).isZero();
    }

    @Test
    void login_locked_account_does_not_prevent_refreshing_an_existing_session() throws Exception {
        String email = "sesionviva@clinica.test";
        MvcResult registered = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
            .andExpect(status().isCreated())
            .andReturn();
        Cookie refresh = registered.getResponse().getCookie(REFRESH_COOKIE);

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            failLogin(email, "mala");
        }
        assertThat(lockedUntilOf(email)).isNotNull();

        // D10: bloquear frena logins NUEVOS; no invalida una sesión ya emitida ni afecta refresh.
        mockMvc.perform(post("/auth/refresh").cookie(refresh))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void expired_lock_does_not_reactivate_a_disabled_identity() throws Exception {
        String email = "expira-deshabilitada@clinica.test";
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123")))
            .andExpect(status().isCreated());
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            failLogin(email, "mala");
        }
        expireLockDirectly(email);
        setStatusDirectly(email, "DISABLED");

        // D12: el lockout automático y el estado administrativo son mecanismos independientes.
        // Expirar la ventana temporal no reactiva una identidad deshabilitada a mano.
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "password123")))
            .andExpect(status().isUnauthorized());
        assertThat(statusOf(email)).isEqualTo("DISABLED");
    }
}
