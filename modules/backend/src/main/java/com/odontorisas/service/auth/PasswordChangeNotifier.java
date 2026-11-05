package com.odontorisas.service.auth;

import com.odontorisas.infra.concurrency.AccountTaskExecutors;
import com.odontorisas.service.auth.AccountMailer.PasswordChangedMail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

/**
 * Aviso por correo de un cambio de contraseña ({@code update-authentication-password-recovery} design.md D6):
 * al restablecerla con el enlace y al cambiarla desde Mi perfil. Se entrega <b>después del commit</b>, así que
 * un cambio rechazado (que revierte) no avisa, y por el executor de <b>avisos</b>, nunca por la cola de pedidos
 * de recuperación: una inundación de pedidos no lo retrasa. Si no se puede enviar, el cambio se mantiene y el
 * fallo se registra sin datos de la contraseña.
 *
 * <p>No depende del contexto de tenant: lo vigila {@code MultiTenancyArchitectureTest}.
 */
@Component
public class PasswordChangeNotifier {

    private static final Logger log = LoggerFactory.getLogger(PasswordChangeNotifier.class);

    private final AccountTaskExecutors executors;
    private final AccountMailer mailer;

    public PasswordChangeNotifier(AccountTaskExecutors executors, AccountMailer mailer) {
        this.executors = executors;
        this.mailer = mailer;
    }

    /** Debe llamarse dentro de la transacción que cambia la contraseña. */
    public void notifyAfterCommit(String recipient, Instant changedAt) {
        PasswordChangedMail mail = new PasswordChangedMail(recipient, changedAt);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("El aviso de cambio de contraseña exige la transacción del cambio.");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit(mail);
            }
        });
    }

    private void submit(PasswordChangedMail mail) {
        try {
            executors.submitNotice(() -> {
                try {
                    mailer.sendPasswordChanged(mail);
                } catch (RuntimeException ex) {
                    log.warn("No se pudo enviar un aviso de cambio de contraseña: {}", ex.getMessage());
                }
            });
        } catch (RuntimeException ex) {
            log.warn("Cola de avisos de cuenta llena o en apagado; se descarta un aviso de cambio de contraseña.");
        }
    }
}
