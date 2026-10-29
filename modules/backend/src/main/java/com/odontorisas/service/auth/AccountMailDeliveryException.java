package com.odontorisas.service.auth;

/**
 * No se pudo entregar un correo de la cuenta. Nunca llega a una respuesta HTTP: estos correos se envían fuera
 * de la petición y el fallo solo se registra ({@code update-authentication-password-recovery} design.md D4).
 */
public class AccountMailDeliveryException extends RuntimeException {
    public AccountMailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
