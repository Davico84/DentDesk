package com.odontorisas.service.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.odontorisas.infra.security.PasswordRecoveryProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Admisión del pedido de recuperación ({@code update-authentication-password-recovery} design.md D10). Corre en
 * la petición y <b>no consulta la base</b>: depende solo de la IP y de los pedidos anteriores de ese correo,
 * nunca de que la cuenta exista. La respuesta es la misma se admita o no.
 *
 * <p><b>Límite por IP, ventana fija:</b> {@code requestsPerIp} pedidos en una ventana que empieza con el primero
 * y no se corre con los siguientes. El incremento es atómico ({@code asMap().compute}): un {@code get} +
 * incremento + {@code put} dejaría pasar más pedidos simultáneos que el límite. La ventana la decide el
 * {@link Clock}; el vencimiento de Caffeine solo libera memoria, con un {@link Expiry} que devuelve lo que queda
 * de la ventana y por eso no la extiende al actualizar. Con el tamaño máximo desbordado Caffeine desaloja
 * contadores y alguna IP recupera cupo: queda medido.
 *
 * <p><b>Un solo pendiente por correo:</b> quien admite reserva el correo, y quien entrega la tarea lo
 * {@link #release libera} siempre: al terminar el worker o si la cola la rechaza.
 */
@Component
public class PasswordRecoveryAdmission {

    public enum Decision { ADMITTED, IP_LIMITED, DUPLICATE }

    /** Ventana fija de una IP: cuándo empezó y cuántos pedidos lleva. */
    record Window(Instant start, int count) {}

    private final Clock clock;
    private final int limit;
    private final Duration window;
    private final Cache<String, Window> perIp;
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final Counter ipLimited;
    private final Counter duplicate;
    private final Counter queueFull;

    public PasswordRecoveryAdmission(PasswordRecoveryProperties properties, Clock clock, MeterRegistry meters) {
        this.clock = clock;
        this.limit = properties.requestsPerIp();
        this.window = properties.ipWindow();
        this.perIp = Caffeine.newBuilder()
            .maximumSize(properties.ipCacheMaxSize())
            .expireAfter(new RemainingWindow())
            .recordStats()
            .build();
        CaffeineCacheMetrics.monitor(meters, perIp, "password_recovery_ip_limit");
        this.ipLimited = notAdmitted(meters, "ip");
        this.duplicate = notAdmitted(meters, "duplicate");
        this.queueFull = notAdmitted(meters, "queue_full");
    }

    private static Counter notAdmitted(MeterRegistry meters, String reason) {
        return Counter.builder("password_recovery.not_admitted")
            .description("Pedidos de recuperación que no se encolaron, por motivo")
            .tag("reason", reason)
            .register(meters);
    }

    /**
     * Decide si el pedido se encola. Cada pedido cuenta para su IP, también el repetido. Si devuelve
     * {@link Decision#ADMITTED}, el correo queda reservado hasta {@link #release}.
     */
    public Decision admit(String clientIp, String normalizedEmail) {
        if (!countWithinLimit(clientIp)) {
            ipLimited.increment();
            return Decision.IP_LIMITED;
        }
        if (!pending.add(normalizedEmail)) {
            duplicate.increment();
            return Decision.DUPLICATE;
        }
        return Decision.ADMITTED;
    }

    /** Libera el correo reservado. Idempotente. */
    public void release(String normalizedEmail) {
        pending.remove(normalizedEmail);
    }

    /** La cola rechazó una tarea admitida (llena o en apagado). */
    public void recordQueueFull() {
        queueFull.increment();
    }

    private boolean countWithinLimit(String clientIp) {
        Instant now = clock.instant();
        Window counted = perIp.asMap().compute(clientIp, (ip, current) ->
            current == null || !now.isBefore(current.start().plus(window))
                ? new Window(now, 1)
                : new Window(current.start(), current.count() + 1));
        return counted.count() <= limit;
    }

    /** Vencimiento = lo que queda de la ventana según el {@link Clock}: una actualización no la extiende. */
    private final class RemainingWindow implements Expiry<String, Window> {
        @Override
        public long expireAfterCreate(String key, Window value, long currentTime) {
            return remainingNanos(value);
        }

        @Override
        public long expireAfterUpdate(String key, Window value, long currentTime, long currentDuration) {
            return remainingNanos(value);
        }

        @Override
        public long expireAfterRead(String key, Window value, long currentTime, long currentDuration) {
            return currentDuration;
        }

        private long remainingNanos(Window value) {
            Duration remaining = Duration.between(clock.instant(), value.start().plus(window));
            return Math.max(0, remaining.toNanos());
        }
    }

    /** Para los tests: cantidad de IPs con contador. */
    long trackedIps() {
        perIp.cleanUp();
        return perIp.estimatedSize();
    }

    /** Para los tests: desalojos del tamaño máximo. */
    long evictions() {
        perIp.cleanUp();
        return perIp.stats().evictionCount();
    }
}
