package com.odontorisas.service.staff;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * La invitación no pudo entregarse por correo (503). Se lanza dentro de la transacción que la
 * emite, así que la revierte: nunca queda una invitación pendiente que nadie recibió.
 */
public class InvitationDeliveryException extends BusinessException {
    public InvitationDeliveryException(String message, Throwable cause) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, "invitation-delivery-failed");
        initCause(cause);
    }

    public InvitationDeliveryException(String message) {
        this(message, null);
    }
}
