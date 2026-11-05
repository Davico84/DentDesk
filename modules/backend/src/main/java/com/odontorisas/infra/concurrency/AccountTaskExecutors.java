package com.odontorisas.infra.concurrency;

import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.infra.security.PasswordRecoveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Los dos executors propios de la cuenta ({@code update-authentication-password-recovery} design.md D4), en
 * lugar de {@code @Async} (prohibido por {@code MultiTenancyArchitectureTest}: un hilo nuevo no hereda el
 * contexto de tenant). Estas tareas <b>no lo necesitan</b>: solo tocan tablas sin RLS. Si alguna tocara una
 * con RLS, {@code TenantAwareTransactionManager} no fijaría nada y fallaría cerrado.
 *
 * <ul>
 *   <li><b>Pedidos de recuperación</b>: 2 hilos. Cualquiera puede generarlos.</li>
 *   <li><b>Avisos de cambio de contraseña</b>: 1 hilo, cola propia. Un aviso solo nace de un cambio que ya
 *       exigió la contraseña o un enlace vigente: una inundación de pedidos no llega a esta cola.</li>
 * </ul>
 *
 * <p>Colas acotadas con {@link ThreadPoolExecutor.AbortPolicy} <b>explícita</b> (D10): un rechazo lanza
 * {@link RejectedExecutionException}, también durante el apagado; nunca se descarta en silencio, para que quien
 * entrega la tarea pueda deshacer lo que reservó. Cada tarea limpia los {@code ThreadLocal} de tenant al
 * empezar, por si un hilo del pool los heredara. No se registran como beans {@code Executor}: no desplazan al
 * executor por defecto de Spring.
 */
@Component
public class AccountTaskExecutors implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AccountTaskExecutors.class);
    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(5);

    private final ThreadPoolExecutor recovery;
    private final ThreadPoolExecutor notice;
    /** Tareas entregadas y sin terminar, de los dos executors: lo usa {@link #awaitIdle}. */
    private final AtomicInteger inFlight = new AtomicInteger();

    public AccountTaskExecutors(PasswordRecoveryProperties properties) {
        this.recovery = executor(2, properties.recoveryQueueCapacity(), "password-recovery-");
        this.notice = executor(1, properties.noticeQueueCapacity(), "account-notice-");
    }

    private static ThreadPoolExecutor executor(int threads, int queueCapacity, String prefix) {
        return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(queueCapacity), threadFactory(prefix), new ThreadPoolExecutor.AbortPolicy());
    }

    private static ThreadFactory threadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, ex) ->
                log.error("Tarea de la cuenta terminó con un error no controlado en {}", t.getName(), ex));
            return thread;
        };
    }

    /** @throws RejectedExecutionException si la cola está llena o el executor se está apagando */
    public void submitRecovery(Runnable task) {
        execute(recovery, task);
    }

    /** @throws RejectedExecutionException si la cola está llena o el executor se está apagando */
    public void submitNotice(Runnable task) {
        execute(notice, task);
    }

    private void execute(ThreadPoolExecutor executor, Runnable task) {
        inFlight.incrementAndGet();
        try {
            executor.execute(() -> {
                try {
                    IdentityContext.clear();
                    OrganizationContext.clear();
                    task.run();
                } finally {
                    inFlight.decrementAndGet();
                }
            });
        } catch (RuntimeException ex) {
            inFlight.decrementAndGet();
            throw ex;
        }
    }

    /**
     * Espera a que no quede ninguna tarea entregada sin terminar. Para los tests, que afirman después de que
     * el trabajo fuera de la petición terminó.
     *
     * @return {@code false} si venció el plazo con tareas pendientes
     */
    public boolean awaitIdle(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (inFlight.get() > 0) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            Thread.sleep(10);
        }
        return true;
    }

    /** Apagado ordenado con un plazo corto; lo que quede en cola se pierde (design.md, Riesgos). */
    @Override
    public void destroy() throws InterruptedException {
        recovery.shutdown();
        notice.shutdown();
        long millis = SHUTDOWN_GRACE.toMillis();
        if (!recovery.awaitTermination(millis, TimeUnit.MILLISECONDS)
            | !notice.awaitTermination(millis, TimeUnit.MILLISECONDS)) {
            log.warn("Apagado con tareas de la cuenta sin terminar; se descartan.");
            recovery.shutdownNow();
            notice.shutdownNow();
        }
    }
}
