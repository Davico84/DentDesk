package com.odontorisas.service.auth;

import com.odontorisas.infra.security.PasswordRecoveryProperties;
import com.odontorisas.service.auth.PasswordRecoveryAdmission.Decision;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admisión del pedido ({@code update-authentication-password-recovery} design.md D10). Requirement
 * <i>Admisión de los pedidos de recuperación</i>: <i>Muchos pedidos desde la misma IP</i>, <i>Pedidos
 * simultáneos desde la misma IP</i>, <i>Pedido repetido mientras otro está pendiente</i>.
 */
class PasswordRecoveryAdmissionTest {

    private static final String IP = "203.0.113.9";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-29T15:00:00Z"));
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private PasswordRecoveryAdmission admission(int ipCacheMaxSize) {
        return new PasswordRecoveryAdmission(new PasswordRecoveryProperties(Duration.ofMinutes(30), 3,
            Duration.ofDays(1), "", 10, Duration.ofHours(1), ipCacheMaxSize, 100, 50), clock, meters);
    }

    private PasswordRecoveryAdmission admission() {
        return admission(10_000);
    }

    /** Pide con correos distintos: solo cuenta el límite por IP, no el de pendientes. */
    private static Decision request(PasswordRecoveryAdmission admission, String ip, int n) {
        return admission.admit(ip, "persona-" + n + "@clinica.test");
    }

    private double notAdmitted(String reason) {
        return meters.get("password_recovery.not_admitted").tag("reason", reason).counter().count();
    }

    @Test
    void the_eleventh_request_from_the_same_ip_is_not_admitted() {
        PasswordRecoveryAdmission admission = admission();
        for (int i = 1; i <= 10; i++) {
            assertThat(request(admission, IP, i)).as("pedido %d", i).isEqualTo(Decision.ADMITTED);
        }

        assertThat(request(admission, IP, 11)).isEqualTo(Decision.IP_LIMITED);
        assertThat(request(admission, "198.51.100.4", 12)).as("otra IP tiene su propio cupo")
            .isEqualTo(Decision.ADMITTED);
        assertThat(notAdmitted("ip")).isEqualTo(1);
    }

    @Test
    void twenty_simultaneous_requests_from_one_ip_admit_exactly_ten() throws Exception {
        PasswordRecoveryAdmission admission = admission();
        // Pool propio: el común de la JVM puede tener un solo hilo en CI.
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Decision>> results = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                int n = i;
                results.add(pool.submit(() -> {
                    start.await();
                    return request(admission, IP, n);
                }));
            }
            start.countDown();
            int admitted = 0;
            for (Future<Decision> result : results) {
                if (result.get(10, TimeUnit.SECONDS) == Decision.ADMITTED) {
                    admitted++;
                }
            }
            assertThat(admitted).isEqualTo(10);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void the_window_is_fixed_from_the_first_request_and_does_not_slide() {
        PasswordRecoveryAdmission admission = admission();
        request(admission, IP, 0);
        clock.advance(Duration.ofMinutes(59));
        for (int i = 1; i < 10; i++) {
            request(admission, IP, i);
        }
        assertThat(request(admission, IP, 10)).as("a los 59 min sigue la misma ventana")
            .isEqualTo(Decision.IP_LIMITED);

        clock.advance(Duration.ofMinutes(1));

        assertThat(request(admission, IP, 11)).as("a los 60 min desde el primero se reinicia")
            .isEqualTo(Decision.ADMITTED);
    }

    /**
     * Con el tamaño máximo desbordado, Caffeine desaloja contadores: la memoria queda acotada y el desalojo
     * queda contado. Qué contador desaloja lo decide su política (W-TinyLFU, que tiende a retener las IPs
     * frecuentes), así que el test no fija cuál.
     */
    @Test
    void eviction_keeps_memory_bounded_and_is_counted() {
        PasswordRecoveryAdmission admission = admission(5);

        for (int i = 0; i < 50; i++) {
            request(admission, "198.51.100." + i, i);
        }

        assertThat(admission.trackedIps()).isLessThanOrEqualTo(5);
        assertThat(admission.evictions()).isPositive();
    }

    @Test
    void an_email_with_a_pending_request_is_not_admitted_again_until_released() {
        PasswordRecoveryAdmission admission = admission();

        assertThat(admission.admit(IP, "ana@clinica.test")).isEqualTo(Decision.ADMITTED);
        assertThat(admission.admit("198.51.100.4", "ana@clinica.test")).isEqualTo(Decision.DUPLICATE);
        assertThat(notAdmitted("duplicate")).isEqualTo(1);

        admission.release("ana@clinica.test");

        assertThat(admission.admit(IP, "ana@clinica.test")).isEqualTo(Decision.ADMITTED);
    }

    /** Reloj que el test adelanta a mano. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
