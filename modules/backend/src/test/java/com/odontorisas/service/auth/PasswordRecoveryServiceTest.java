package com.odontorisas.service.auth;

import com.odontorisas.infra.concurrency.AccountTaskExecutors;
import com.odontorisas.infra.security.PasswordRecoveryProperties;
import com.odontorisas.service.auth.PasswordRecoveryAdmission.Decision;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * El pedido en la petición ({@code update-authentication-password-recovery} design.md D3, D9, D10): no toca la
 * cuenta, y el correo reservado se libera siempre, pase lo que pase con la tarea.
 */
class PasswordRecoveryServiceTest {

    private static final String EMAIL = "ana@clinica.test";
    private static final String IP = "203.0.113.9";

    /** 2 hilos de pedidos (fijo) y cola de 1. */
    private final PasswordRecoveryProperties properties = new PasswordRecoveryProperties(Duration.ofMinutes(30), 3,
        Duration.ofDays(1), "", 100, Duration.ofHours(1), 10_000, 1, 50);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final PasswordRecoveryAdmission admission =
        new PasswordRecoveryAdmission(properties, Clock.systemUTC(), meters);
    private final AccountTaskExecutors executors = new AccountTaskExecutors(properties);
    private final PasswordRecoveryWorker worker = mock(PasswordRecoveryWorker.class);
    private final PasswordRecoveryService service = new PasswordRecoveryService(admission, executors, worker);

    @AfterEach
    void shutdown() throws InterruptedException {
        executors.destroy();
    }

    /** ¿El correo quedó libre? Se prueba admitiéndolo desde otra IP y soltándolo enseguida. */
    private boolean isReleased(String email) {
        Decision decision = admission.admit("198.51.100.250", email);
        admission.release(email);
        return decision == Decision.ADMITTED;
    }

    @Test
    void the_request_does_not_touch_the_worker_nor_its_repositories_on_the_request_thread() {
        AccountTaskExecutors capturing = mock(AccountTaskExecutors.class);
        PasswordRecoveryService onlyQueues = new PasswordRecoveryService(admission, capturing, worker);

        onlyQueues.request("  Ana@Clinica.TEST ", IP);

        // El worker es lo único que toca la base: en la petición no se lo llama.
        verifyNoInteractions(worker);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(capturing).submitRecovery(task.capture());
        task.getValue().run();
        verify(worker).process(EMAIL);
    }

    @Test
    void with_the_queue_full_the_email_is_released() throws Exception {
        CountDownLatch hold = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(2);
        doAnswer(invocation -> {
            started.countDown();
            hold.await(10, TimeUnit.SECONDS);
            return null;
        }).when(worker).process(anyString());
        try {
            service.request("uno@clinica.test", IP);
            service.request("dos@clinica.test", IP);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            service.request("tres@clinica.test", IP); // ocupa la cola de 1

            service.request(EMAIL, IP); // rechazado

            assertThat(isReleased(EMAIL)).isTrue();
            assertThat(meters.get("password_recovery.not_admitted").tag("reason", "queue_full").counter().count())
                .isEqualTo(1);
        } finally {
            hold.countDown();
        }
    }

    @Test
    void after_shutdown_the_email_is_released() throws Exception {
        executors.destroy();

        service.request(EMAIL, IP);

        assertThat(isReleased(EMAIL)).isTrue();
    }

    @Test
    void when_the_worker_throws_a_runtime_exception_the_email_is_released() throws Exception {
        doThrow(new IllegalStateException("falla")).when(worker).process(anyString());

        service.request(EMAIL, IP);

        assertThat(executors.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(isReleased(EMAIL)).isTrue();
    }

    @Test
    void when_the_worker_throws_an_error_the_email_is_released() throws Exception {
        doThrow(new AssertionError("falla grave")).when(worker).process(anyString());

        service.request(EMAIL, IP);

        assertThat(executors.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(isReleased(EMAIL)).isTrue();
    }

    @Test
    void after_the_worker_finishes_the_email_is_released() throws Exception {
        service.request(EMAIL, IP);

        assertThat(executors.awaitIdle(Duration.ofSeconds(5))).isTrue();
        verify(worker).process(EMAIL);
        assertThat(isReleased(EMAIL)).isTrue();
    }
}
