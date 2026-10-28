package com.odontorisas.auth;

import com.odontorisas.AbstractIntegrationTest;
import com.odontorisas.common.Role;
import com.odontorisas.infra.security.TokenService;
import com.odontorisas.service.auth.AuthConcurrencyProbe;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Carreras del cambio de contraseña ({@code update-authentication-my-profile} design.md D3, tarea 2.5). El
 * intercalado se fuerza con compuertas en {@link AuthConcurrencyProbe}, no con el azar del timing: un hilo
 * se detiene justo en la ventana de la carrera y el otro corre entero (o queda esperando el bloqueo de la
 * fila, que se detecta con {@code awaitLockWaitOrFinished}).
 *
 * <p>Verificado a mano (2026-10-27) que (a) y (b) <b>fallan sin el protocolo</b>:
 * <ul>
 *   <li>(a) sin el {@code FOR SHARE} de {@code refresh}: <b>deadlock</b> detectado por Postgres y 500. El
 *   cambio tiene el {@code FOR UPDATE} de {@code users} y espera la fila de {@code refresh_tokens} que la
 *   renovación borró; la renovación, al insertar su token nuevo, toma {@code FOR KEY SHARE} de {@code users}
 *   por la FK y espera al cambio.</li>
 *   <li>(b) sin la condición del hash en {@code resetLoginAttemptsIfNotLocked}: el login emite una sesión con
 *   la contraseña anterior que sigue renovándose después del cambio.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ChangePasswordConcurrencyIT.Gates.class)
class ChangePasswordConcurrencyIT extends AbstractIntegrationTest {

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String CURRENT = "actual-123";
    private static final String NEW = "nueva-4567";
    private static final long TIMEOUT_SECONDS = 20;

    /** Una compuerta: el primer hilo que la cruza se detiene hasta {@link #open()}; los demás pasan. */
    static final class Gate {
        private final CountDownLatch reached = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        void hold() {
            reached.countDown();
            try {
                if (!released.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("La compuerta no se abrió en " + TIMEOUT_SECONDS + " s");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void awaitReached() throws InterruptedException {
            assertThat(reached.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("el hilo llegó a la compuerta").isTrue();
        }

        void open() {
            released.countDown();
        }
    }

    /** Probe con una compuerta armable por punto; {@code getAndSet(null)}: solo detiene al primero. */
    static final class GateProbe implements AuthConcurrencyProbe {
        final AtomicReference<Gate> loginVerified = new AtomicReference<>();
        final AtomicReference<Gate> refreshConsumed = new AtomicReference<>();
        final AtomicReference<Gate> changeVerified = new AtomicReference<>();
        final AtomicReference<Gate> changeLocked = new AtomicReference<>();

        private static void pass(AtomicReference<Gate> point) {
            Gate gate = point.getAndSet(null);
            if (gate != null) {
                gate.hold();
            }
        }

        @Override public void afterLoginPasswordVerified() { pass(loginVerified); }
        @Override public void afterRefreshTokenConsumed() { pass(refreshConsumed); }
        @Override public void afterChangePasswordVerified() { pass(changeVerified); }
        @Override public void afterChangePasswordLocked() { pass(changeLocked); }

        void disarm() {
            loginVerified.set(null);
            refreshConsumed.set(null);
            changeVerified.set(null);
            changeLocked.set(null);
        }
    }

    @TestConfiguration
    static class Gates {
        @Bean
        GateProbe gateProbe() {
            return new GateProbe();
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired GateProbe probe;

    private long clinic;
    private long user;
    private String email;
    private ExecutorService pool;

    @BeforeEach
    void aUserAndAPool() throws SQLException {
        String suffix = UUID.randomUUID().toString();
        email = "cpc-" + suffix + "@test.local";
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            clinic = id(s, "INSERT INTO organizations (name, slug) VALUES ('C', 'cpc-" + suffix + "') RETURNING id");
            user = id(s, "INSERT INTO users (email, password_hash, full_name) VALUES ('" + email + "', '"
                + passwordEncoder.encode(CURRENT) + "', 'Dra. Carrera') RETURNING id");
            s.execute("INSERT INTO memberships (user_id, organization_id, role, status) VALUES ("
                + user + ", " + clinic + ", 'ADMIN', 'ACTIVE')");
        }
        // Hilos propios: el pool común de CompletableFuture es de un hilo en CI.
        pool = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopPoolAndDisarm() {
        probe.disarm();
        pool.shutdownNow();
    }

    private static long id(Statement s, String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String column(String column) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT " + column + " FROM users WHERE id = " + user)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private MockHttpServletResponse change(String current, String next) throws Exception {
        return mockMvc.perform(post("/auth/password")
                .header("Authorization", "Bearer " + tokenService.issueAccessToken(user, email, clinic, Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"))
            .andReturn().getResponse();
    }

    private MockHttpServletResponse login(String password) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andReturn().getResponse();
    }

    private MockHttpServletResponse refresh(Cookie cookie) throws Exception {
        return mockMvc.perform(post("/auth/refresh").cookie(cookie)).andReturn().getResponse();
    }

    /** Lanza {@code work} en el pool y cuenta {@code finished} al terminar (bien o mal). */
    private Future<MockHttpServletResponse> async(CountDownLatch finished,
                                                  java.util.concurrent.Callable<MockHttpServletResponse> work) {
        return pool.submit(() -> {
            try {
                return work.call();
            } finally {
                finished.countDown();
            }
        });
    }

    private static MockHttpServletResponse result(Future<MockHttpServletResponse> future) throws Exception {
        return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    // ================================================================ (a) renovación contra cambio

    @Test
    void a_refresh_racing_a_change_never_leaves_the_other_session_with_a_valid_refresh() throws Exception {
        Cookie other = login(CURRENT).getCookie(REFRESH_COOKIE);
        Gate gate = new Gate();
        probe.refreshConsumed.set(gate);

        CountDownLatch refreshFinished = new CountDownLatch(1);
        CountDownLatch changeFinished = new CountDownLatch(1);
        Future<MockHttpServletResponse> renewal = async(refreshFinished, () -> refresh(other));
        gate.awaitReached(); // token consumido, el nuevo sin insertar, FOR SHARE tomado
        Future<MockHttpServletResponse> changed = async(changeFinished, () -> change(CURRENT, NEW));
        awaitLockWaitOrFinished(changeFinished); // con el protocolo, el cambio espera el FOR SHARE
        gate.open();

        MockHttpServletResponse renewed = result(renewal);
        assertThat(result(changed).getStatus()).isEqualTo(200);
        assertThat(renewed.getStatus()).isEqualTo(200);
        // El token que la renovación insertó tiene que haber caído con el cambio.
        assertThat(refresh(renewed.getCookie(REFRESH_COOKIE)).getStatus()).isEqualTo(401);
    }

    // ================================================================ (b) login contra cambio

    @Test
    void a_login_with_the_old_password_racing_a_change_leaves_no_living_session() throws Exception {
        Gate gate = new Gate();
        probe.loginVerified.set(gate);

        CountDownLatch loginFinished = new CountDownLatch(1);
        CountDownLatch changeFinished = new CountDownLatch(1);
        Future<MockHttpServletResponse> loggingIn = async(loginFinished, () -> login(CURRENT));
        gate.awaitReached(); // BCrypt con la contraseña anterior ya pasó, sin bloqueo tomado
        Future<MockHttpServletResponse> changed = async(changeFinished, () -> change(CURRENT, NEW));
        assertThat(result(changed).getStatus()).isEqualTo(200);
        gate.open();

        MockHttpServletResponse loggedIn = result(loggingIn);
        if (loggedIn.getStatus() == 200) {
            assertThat(refresh(loggedIn.getCookie(REFRESH_COOKIE)).getStatus())
                .as("una sesión iniciada con la contraseña anterior no puede sobrevivir al cambio").isEqualTo(401);
        } else {
            assertThat(loggedIn.getStatus()).isEqualTo(401);
        }
        assertThat(loggedIn.getStatus()).as("con el protocolo, el login se rechaza").isEqualTo(401);
    }

    // ================================================================ (c) contraseña incorrecta contra cambio

    @Test
    void a_wrong_password_racing_a_change_does_not_hang_and_the_failure_is_counted() throws Exception {
        Gate gate = new Gate();
        probe.changeLocked.set(gate);

        CountDownLatch changeFinished = new CountDownLatch(1);
        CountDownLatch wrongFinished = new CountDownLatch(1);
        Future<MockHttpServletResponse> changed = async(changeFinished, () -> change(CURRENT, NEW));
        gate.awaitReached(); // el cambio tiene el FOR UPDATE
        Future<MockHttpServletResponse> wrong = async(wrongFinished, () -> change("no-es-esta", "otra-12345"));
        awaitLockWaitOrFinished(wrongFinished); // recordFailure espera la fila, en su propia transacción
        gate.open();

        assertThat(result(changed).getStatus()).isEqualTo(200);
        assertThat(result(wrong).getStatus()).isEqualTo(400);
        // El cambio reinició el contador al confirmar; el fallo, que esperaba, cuenta después.
        assertThat(column("failed_login_attempts")).isEqualTo("1");
    }

    // ================================================================ (d) dos cambios simultáneos

    @Test
    void two_changes_with_the_same_current_password_one_wins_and_the_other_gets_400() throws Exception {
        Gate gate = new Gate();
        probe.changeVerified.set(gate);

        CountDownLatch firstFinished = new CountDownLatch(1);
        CountDownLatch secondFinished = new CountDownLatch(1);
        Future<MockHttpServletResponse> first = async(firstFinished, () -> change(CURRENT, "primera-123"));
        gate.awaitReached(); // el primero verificó la actual y todavía no bloqueó
        Future<MockHttpServletResponse> second = async(secondFinished, () -> change(CURRENT, "segunda-123"));
        assertThat(result(second).getStatus()).isEqualTo(200);
        gate.open();

        MockHttpServletResponse loser = result(first);
        assertThat(loser.getStatus()).isEqualTo(400);
        assertThat(loser.getContentAsString()).contains("current-password-incorrect");
        // El perdedor no cuenta como fallo: la contraseña era correcta al verificarla.
        assertThat(column("failed_login_attempts")).isEqualTo("0");
        assertThat(login("segunda-123").getStatus()).isEqualTo(200);
        assertThat(login("primera-123").getStatus()).isEqualTo(401);
    }
}
