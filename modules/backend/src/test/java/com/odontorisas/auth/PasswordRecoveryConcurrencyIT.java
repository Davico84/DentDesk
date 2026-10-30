package com.odontorisas.auth;

import com.odontorisas.service.auth.AuthConcurrencyProbe;
import com.odontorisas.service.auth.PasswordRecoveryWorker;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Carreras del pedido y del restablecimiento ({@code update-authentication-password-recovery} design.md D3, D5,
 * tarea 2.7). El intercalado se fuerza con compuertas en {@link AuthConcurrencyProbe}, como en
 * {@code ChangePasswordConcurrencyIT}: un hilo se detiene justo en la ventana de la carrera.
 *
 * <p>Verificado a mano (2026-10-30) que (a), (b) y (e) <b>fallan sin su mecanismo</b>:
 * <ul>
 *   <li>(a) sin los {@code FOR UPDATE} del restablecimiento (users y token): los dos ven el enlace vigente y los dos
 *   cambian la contraseña. Con solo el del token también queda serializado.</li>
 *   <li>(b) sin el {@code FOR UPDATE} de users en el worker: 4 envíos en vez de 3.</li>
 *   <li>(d') login verificado antes del restablecimiento, sin exigir el hash verificado al cerrar el login: entra con
 *   la contraseña anterior (200). Login que termina mientras el restablecimiento espera, sin borrar las sesiones:
 *   su sesión se renueva.</li>
 *   <li>(e) comprobando el estado antes del bloqueo (con la copia cargada antes): se emite el enlace a la cuenta ya
 *   desactivada o ya de plataforma.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PasswordRecoveryConcurrencyIT.Gates.class)
class PasswordRecoveryConcurrencyIT extends AbstractPasswordRecoveryIT {

    private static final long TIMEOUT_SECONDS = 20;
    private static final String REFRESH_COOKIE = "refresh_token";

    /** Una compuerta: los hilos que la cruzan se detienen hasta {@link #open()}. */
    static final class Gate {
        private final CountDownLatch reached;
        private final CountDownLatch released = new CountDownLatch(1);

        Gate(int expected) {
            this.reached = new CountDownLatch(expected);
        }

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
            assertThat(reached.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("los hilos llegaron a la compuerta").isTrue();
        }

        void open() {
            released.countDown();
        }
    }

    /**
     * Probe con una compuerta armable por punto. {@code single}: detiene solo al primero que la cruza; si no, a
     * todos los que la crucen mientras esté armada.
     */
    static final class GateProbe implements AuthConcurrencyProbe {
        final AtomicReference<Gate> recoveryUserFound = new AtomicReference<>();
        final AtomicReference<Gate> resetLinkValidated = new AtomicReference<>();
        final AtomicReference<Gate> refreshConsumed = new AtomicReference<>();
        final AtomicReference<Gate> loginVerified = new AtomicReference<>();
        final AtomicReference<Gate> resetTokenRead = new AtomicReference<>();
        volatile boolean single = true;

        private void pass(AtomicReference<Gate> point) {
            Gate gate = single ? point.getAndSet(null) : point.get();
            if (gate != null) {
                gate.hold();
            }
        }

        @Override public void afterRecoveryUserFound() { pass(recoveryUserFound); }
        @Override public void afterResetLinkValidated() { pass(resetLinkValidated); }
        @Override public void afterRefreshTokenConsumed() { pass(refreshConsumed); }
        @Override public void afterLoginPasswordVerified() { pass(loginVerified); }
        @Override public void afterResetTokenRead() { pass(resetTokenRead); }

        void disarm() {
            recoveryUserFound.set(null);
            resetLinkValidated.set(null);
            refreshConsumed.set(null);
            loginVerified.set(null);
            resetTokenRead.set(null);
            single = true;
        }
    }

    @TestConfiguration
    static class Gates {
        @Bean
        GateProbe gateProbe() {
            return new GateProbe();
        }
    }

    @Autowired GateProbe probe;
    @Autowired PasswordRecoveryWorker worker;

    private ExecutorService pool;

    @BeforeEach
    void aPool() {
        // Hilos propios: el pool común de CompletableFuture es de un hilo en CI.
        pool = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void stopPoolAndDisarm() {
        probe.disarm();
        pool.shutdownNow();
    }

    private <T> Future<T> async(CountDownLatch finished, Callable<T> work) {
        return pool.submit(() -> {
            try {
                return work.call();
            } finally {
                finished.countDown();
            }
        });
    }

    private static <T> T result(Future<T> future) throws Exception {
        return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private long validLinks(long user) throws Exception {
        return count("SELECT count(*) FROM password_reset_tokens WHERE user_id = " + user
            + " AND used_at IS NULL AND revoked_at IS NULL AND expires_at > now()");
    }

    // ================================================================ (a) el mismo enlace dos veces a la vez

    @Test
    void two_resets_with_the_same_link_let_only_one_change_the_password() throws Exception {
        String email = newEmail("doble");
        long user = activeMember(email);
        String token = link(user, Duration.ofMinutes(1));
        Gate gate = new Gate(1);
        probe.resetLinkValidated.set(gate);

        CountDownLatch firstFinished = new CountDownLatch(1);
        Future<MockHttpServletResponse> first = async(firstFinished, () -> reset(token, "primera-123"));
        gate.awaitReached();
        CountDownLatch secondFinished = new CountDownLatch(1);
        Future<MockHttpServletResponse> second = async(secondFinished, () -> reset(token, "segunda-456"));
        awaitLockWaitOrFinished(secondFinished);
        gate.open();

        assertThat(result(first).getStatus()).isEqualTo(200);
        assertThat(result(second).getStatus()).isEqualTo(400);
        assertThat(login(email, "primera-123")).isEqualTo(200);
        assertThat(login(email, "segunda-456")).isEqualTo(401);
    }

    // ================================================================ (b) cuatro pedidos a la vez

    @Test
    void four_simultaneous_requests_of_one_account_send_exactly_three_links() throws Exception {
        String email = newEmail("cuatro");
        long user = activeMember(email);
        Gate gate = new Gate(4);
        probe.single = false;
        probe.recoveryUserFound.set(gate);

        CountDownLatch finished = new CountDownLatch(4);
        List<Future<Object>> runs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            // Directo al worker: la admisión dejaría uno solo pendiente por correo.
            runs.add(async(finished, () -> {
                worker.process(email.toLowerCase());
                return null;
            }));
        }
        gate.awaitReached();
        probe.recoveryUserFound.set(null);
        gate.open();
        for (Future<Object> run : runs) {
            result(run);
        }

        assertThat(sentLinksTo(email)).hasSize(3);
        assertThat(count("SELECT count(*) FROM password_reset_tokens WHERE user_id = " + user)).isEqualTo(3);
    }

    // ================================================================ (c) pedido nuevo contra restablecimiento

    @Test
    void a_new_request_racing_a_reset_with_the_previous_link_does_not_deadlock() throws Exception {
        String email = newEmail("cruce");
        long user = activeMember(email);
        String previous = link(user, Duration.ofMinutes(1));
        Gate gate = new Gate(1);
        probe.resetLinkValidated.set(gate);

        CountDownLatch resetFinished = new CountDownLatch(1);
        Future<MockHttpServletResponse> resetting = async(resetFinished, () -> reset(previous, NEW_PASSWORD));
        gate.awaitReached();
        CountDownLatch requestFinished = new CountDownLatch(1);
        Future<Object> requesting = async(requestFinished, () -> {
            worker.process(email.toLowerCase());
            return null;
        });
        awaitLockWaitOrFinished(requestFinished);
        gate.open();

        assertThat(result(resetting).getStatus()).isEqualTo(200);
        result(requesting);
        // El pedido, serializado detrás del restablecimiento, emitió un enlace nuevo y vigente.
        assertThat(sentLinksTo(email)).hasSize(1);
        assertThat(validLinks(user)).isEqualTo(1);
        assertThat(check(sentLinksTo(email).get(0).rawToken()).getStatus()).isEqualTo(204);
    }

    // ================================================================ (d) renovación contra restablecimiento

    @Test
    void a_refresh_racing_a_reset_leaves_no_session_that_can_renew() throws Exception {
        String email = newEmail("renueva");
        long user = activeMember(email);
        Cookie other = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andReturn().getResponse().getCookie(REFRESH_COOKIE);
        String token = link(user, Duration.ofMinutes(1));
        Gate gate = new Gate(1);
        probe.refreshConsumed.set(gate);

        CountDownLatch refreshFinished = new CountDownLatch(1);
        Future<MockHttpServletResponse> refreshing = async(refreshFinished,
            () -> mockMvc.perform(post("/auth/refresh").cookie(other)).andReturn().getResponse());
        gate.awaitReached();
        CountDownLatch resetFinished = new CountDownLatch(1);
        Future<MockHttpServletResponse> resetting = async(resetFinished, () -> reset(token, NEW_PASSWORD));
        awaitLockWaitOrFinished(resetFinished);
        gate.open();

        MockHttpServletResponse renewed = result(refreshing);
        assertThat(result(resetting).getStatus()).isEqualTo(200);
        assertThat(count("SELECT count(*) FROM refresh_tokens WHERE user_id = " + user)).isZero();
        Cookie fresh = renewed.getCookie(REFRESH_COOKIE);
        if (fresh != null) {
            assertThat(mockMvc.perform(post("/auth/refresh").cookie(fresh)).andReturn().getResponse().getStatus())
                .isEqualTo(401);
        }
    }

    // ================================================================ (d') login con la contraseña anterior contra restablecimiento

    @Test
    void a_login_with_the_old_password_verified_before_a_reset_is_rejected_after_it() throws Exception {
        String email = newEmail("login-antes");
        long user = activeMember(email);
        String token = link(user, Duration.ofMinutes(1));
        Gate gate = new Gate(1);
        probe.loginVerified.set(gate);

        CountDownLatch loginFinished = new CountDownLatch(1);
        Future<Integer> logging = async(loginFinished, () -> login(email, PASSWORD));
        gate.awaitReached();
        // El BCrypt del login ya aceptó la contraseña anterior; el restablecimiento commitea en el medio.
        assertThat(reset(token, NEW_PASSWORD).getStatus()).isEqualTo(200);
        gate.open();

        assertThat(result(logging)).isEqualTo(401);
        assertThat(count("SELECT count(*) FROM refresh_tokens WHERE user_id = " + user)).isZero();
        assertThat(login(email, NEW_PASSWORD)).isEqualTo(200);
    }

    @Test
    void a_login_that_completes_while_a_reset_waits_cannot_renew_its_session() throws Exception {
        String email = newEmail("login-durante");
        long user = activeMember(email);
        String token = link(user, Duration.ofMinutes(1));
        Gate gate = new Gate(1);
        probe.resetTokenRead.set(gate);

        CountDownLatch resetFinished = new CountDownLatch(1);
        Future<MockHttpServletResponse> resetting = async(resetFinished, () -> reset(token, NEW_PASSWORD));
        gate.awaitReached();
        // El restablecimiento todavía no tomó ningún bloqueo: el login con la contraseña anterior entra.
        Cookie session = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andReturn().getResponse().getCookie(REFRESH_COOKIE);
        assertThat(session).isNotNull();
        gate.open();

        assertThat(result(resetting).getStatus()).isEqualTo(200);
        assertThat(count("SELECT count(*) FROM refresh_tokens WHERE user_id = " + user)).isZero();
        assertThat(mockMvc.perform(post("/auth/refresh").cookie(session)).andReturn().getResponse().getStatus())
            .isEqualTo(401);
    }

    // ================================================================ (e) el estado cambia mientras se procesa

    @Test
    void an_account_disabled_while_its_request_is_processed_gets_no_link() throws Exception {
        String email = newEmail("se-desactiva");
        long user = activeMember(email);
        Gate gate = new Gate(1);
        probe.recoveryUserFound.set(gate);

        CountDownLatch finished = new CountDownLatch(1);
        Future<Object> processing = async(finished, () -> {
            worker.process(email.toLowerCase());
            return null;
        });
        gate.awaitReached();
        sql("UPDATE users SET status = 'DISABLED' WHERE id = " + user);
        gate.open();
        result(processing);

        assertThat(sentLinksTo(email)).isEmpty();
        assertThat(count("SELECT count(*) FROM password_reset_tokens WHERE user_id = " + user)).isZero();
    }

    @Test
    void an_account_promoted_to_platform_while_its_request_is_processed_gets_no_link() throws Exception {
        String email = newEmail("se-promueve");
        long user = activeMember(email);
        Gate gate = new Gate(1);
        probe.recoveryUserFound.set(gate);

        CountDownLatch finished = new CountDownLatch(1);
        Future<Object> processing = async(finished, () -> {
            worker.process(email.toLowerCase());
            return null;
        });
        gate.awaitReached();
        platformAdmin(user);
        gate.open();
        result(processing);

        assertThat(sentLinksTo(email)).isEmpty();
        assertThat(count("SELECT count(*) FROM password_reset_tokens WHERE user_id = " + user)).isZero();
    }
}
