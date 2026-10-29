package com.odontorisas.service.auth;

import java.time.Instant;

/**
 * Correos de la cuenta ({@code update-authentication-password-recovery} design.md D4, D6): el enlace de
 * recuperación y el aviso de cambio de contraseña. Separado de {@code InvitationMailer} a propósito: una
 * invitación que no se envía <b>debe</b> fallar su petición; estos correos se envían fuera de la petición y un
 * fallo solo se registra. Sin correo configurado, la variante deshabilitada registra y descarta.
 *
 * <p>Las implementaciones lanzan {@link AccountMailDeliveryException} si no pueden entregar, y nunca ponen el
 * token, el enlace ni datos de la contraseña en el mensaje de la excepción.
 */
public interface AccountMailer {

    /** Enlace de recuperación: el token en claro solo viaja en este correo. */
    record RecoveryMail(String recipient, String rawToken, Instant expiresAt) {
        @Override
        public String toString() {
            return "RecoveryMail[recipient=" + recipient + ", expiresAt=" + expiresAt + "]";
        }
    }

    /** Aviso de que la contraseña de la cuenta cambió, con la fecha y hora del cambio. */
    record PasswordChangedMail(String recipient, Instant changedAt) {}

    void sendRecoveryLink(RecoveryMail mail);

    void sendPasswordChanged(PasswordChangedMail mail);
}
