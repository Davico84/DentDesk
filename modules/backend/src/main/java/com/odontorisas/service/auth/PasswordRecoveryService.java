package com.odontorisas.service.auth;

import com.odontorisas.infra.concurrency.AccountTaskExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * El pedido de recuperación en la petición ({@code update-authentication-password-recovery} design.md D3, D10):
 * normaliza el correo, lo admite y, si se admitió, lo entrega al executor de pedidos. <b>No toca la base ni
 * busca la cuenta</b>: es el mismo trabajo para cualquier correo, y la respuesta es la misma se encole o no.
 *
 * <p>El correo reservado por la admisión se libera siempre: si la cola rechaza la tarea (llena o en apagado),
 * acá mismo; si se encoló, en el {@code finally} de la tarea, que rodea todo el trabajo del worker y corre
 * también ante un {@link Error}.
 */
@Service
public class PasswordRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryService.class);

    private final PasswordRecoveryAdmission admission;
    private final AccountTaskExecutors executors;
    private final PasswordRecoveryWorker worker;

    public PasswordRecoveryService(PasswordRecoveryAdmission admission, AccountTaskExecutors executors,
                                   PasswordRecoveryWorker worker) {
        this.admission = admission;
        this.executors = executors;
        this.worker = worker;
    }

    /** {@code email} ya llega con formato válido (lo valida el DTO). */
    public void request(String email, String clientIp) {
        String normalized = normalize(email);
        if (admission.admit(clientIp, normalized) != PasswordRecoveryAdmission.Decision.ADMITTED) {
            return;
        }
        try {
            executors.submitRecovery(() -> {
                try {
                    worker.process(normalized);
                } finally {
                    admission.release(normalized);
                }
            });
        } catch (RuntimeException ex) {
            admission.release(normalized);
            admission.recordQueueFull();
            log.warn("Cola de pedidos de recuperación llena o en apagado; se descarta un pedido.");
        }
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
