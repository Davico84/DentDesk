package com.odontorisas.auth;

import com.odontorisas.service.auth.AuthConcurrencyProbe;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Anti-enumeración del pedido ({@code update-authentication-password-recovery} design.md D9, tarea 2.8). Con el
 * worker de recuperación <b>detenido</b>, la petición tiene que responder igual para cualquier cuenta, y sin
 * haber tocado la base: si consultara la cuenta antes de encolar, su respuesta (o su tiempo) podría depender de
 * ella. Que la petición no ejecute ninguna sentencia se prueba <b>por hilo</b>: con MockMvc la petición corre en
 * el hilo del test, y un {@link StatementInspector} de Hibernate registra el hilo de cada sentencia SQL. Un contador
 * global no alcanzaría: Hikari, Actuator y otros hilos lo mueven. (Envolver el {@code DataSource} no sirve: Flyway
 * deriva el suyo del bean principal y no reconoce un tipo envoltorio.)
 *
 * <p>Verificado a mano (2026-10-30) que el test <b>falla</b> si la petición consulta la cuenta antes de encolar
 * (una búsqueda por correo en {@code PasswordRecoveryService.request}): el inspector ve el hilo de la petición.
 *
 * <p>Requirements: <i>Pedir la recuperación de la contraseña</i> (<i>La respuesta no delata la cuenta</i>) y
 * <i>Admisión de los pedidos de recuperación</i> (<i>Pedido repetido mientras otro está pendiente</i> y las
 * variantes no atendidas).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
    + "com.odontorisas.auth.PasswordRecoveryAntiEnumerationIT$RecordingInspector")
@AutoConfigureMockMvc
@Import(PasswordRecoveryAntiEnumerationIT.Hooks.class)
class PasswordRecoveryAntiEnumerationIT extends AbstractPasswordRecoveryIT {

    /** Detiene <b>todos</b> los workers de recuperación mientras esté armado. */
    static final class HoldingProbe implements AuthConcurrencyProbe {
        volatile CountDownLatch hold;
        final Set<Thread> started = ConcurrentHashMap.newKeySet();

        @Override
        public void beforeRecoveryProcessed() {
            CountDownLatch latch = hold;
            if (latch != null) {
                started.add(Thread.currentThread());
                await(latch);
            }
        }
    }

    /** Registra el hilo de cada sentencia SQL que ejecuta Hibernate. Lo instancia Hibernate, por nombre. */
    public static final class RecordingInspector implements StatementInspector {
        static final Set<Thread> THREADS = ConcurrentHashMap.newKeySet();

        @Override
        public String inspect(String sql) {
            THREADS.add(Thread.currentThread());
            return sql;
        }
    }

    @TestConfiguration
    static class Hooks {
        @Bean
        HoldingProbe holdingProbe() {
            return new HoldingProbe();
        }
    }

    @Autowired HoldingProbe probe;

    @AfterEach
    void release() {
        CountDownLatch latch = probe.hold;
        probe.hold = null;
        if (latch != null) {
            latch.countDown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Status, cuerpo y headers uno por uno. Se excluyen por nombre solo los que cambian en cada petición sin
     * depender de nada: {@code Date} y {@code X-Trace-Id} (aleatorio, para correlacionar logs).
     */
    private static String fingerprint(MockHttpServletResponse response) throws Exception {
        Map<String, Object> headers = new TreeMap<>();
        for (String name : response.getHeaderNames()) {
            if (!name.equalsIgnoreCase("Date") && !name.equalsIgnoreCase("X-Trace-Id")) {
                headers.put(name.toLowerCase(), response.getHeaders(name));
            }
        }
        return response.getStatus() + "|" + headers + "|" + response.getContentAsString();
    }

    @Test
    void every_case_gets_an_identical_response_without_the_request_touching_the_database() throws Exception {
        String active = newEmail("activa");
        activeMember(active);
        String disabled = newEmail("desactivada");
        user(disabled, "DISABLED");
        String platform = newEmail("plataforma");
        platformAdmin(user(platform, "ACTIVE"));
        String platformMember = newEmail("plataforma-miembro");
        platformAdmin(activeMember(platformMember));
        String overLimit = newEmail("limite");
        long overLimitUser = activeMember(overLimit);
        for (int i = 1; i <= 3; i++) {
            link(overLimitUser, Duration.ofHours(i));
        }
        String unregistered = newEmail("nadie");

        CountDownLatch hold = new CountDownLatch(1);
        probe.hold = hold;
        RecordingInspector.THREADS.clear();
        Thread requestThread = Thread.currentThread();

        String expected = fingerprint(recover(active));
        for (String email : new String[] {unregistered, disabled, platform, platformMember, overLimit}) {
            assertThat(fingerprint(recover(email))).as(email).isEqualTo(expected);
        }

        assertThat(RecordingInspector.THREADS).as("la petición no ejecutó ninguna sentencia").doesNotContain(requestThread);
        assertThat(sentLinks()).as("el worker todavía no empezó a enviar").isEmpty();

        // Variantes que no se atienden: tienen que responder exactamente igual.
        assertThat(fingerprint(recover(active))).as("repetido con otro pendiente").isEqualTo(expected);

        ip = "192.0.2.77";
        for (int i = 0; i < 10; i++) {
            recover(newEmail("relleno"));
        }
        assertThat(fingerprint(recover(newEmail("limite-ip")))).as("límite de IP").isEqualTo(expected);

        ip = "192.0.2.78";
        fillTheRecoveryQueue(hold);
        assertThat(fingerprint(recover(newEmail("cola-llena")))).as("cola llena").isEqualTo(expected);
        assertThat(RecordingInspector.THREADS).as("tampoco en las variantes").doesNotContain(requestThread);

        probe.hold = null;
        hold.countDown();
        awaitIdle();

        assertThat(sentLinks()).as("solo la cuenta activa dentro del límite, una sola vez")
            .extracting(mail -> mail.recipient()).containsExactly(active);
    }

    /** Ocupa lo que quede de la cola con tareas que esperan a {@code hold}. */
    private void fillTheRecoveryQueue(CountDownLatch hold) {
        try {
            for (int i = 0; i < 1_000; i++) {
                executors.submitRecovery(() -> await(hold));
            }
        } catch (RejectedExecutionException full) {
            return;
        }
        throw new IllegalStateException("La cola de recuperación no se llenó");
    }
}
