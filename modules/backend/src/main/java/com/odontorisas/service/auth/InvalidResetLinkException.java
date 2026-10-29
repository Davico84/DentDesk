package com.odontorisas.service.auth;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Enlace de recuperación inválido, vencido, usado, anulado o de una cuenta de plataforma: el mismo 400 para
 * todos, sin decir cuál ({@code update-authentication-password-recovery} design.md D1, D5).
 */
public class InvalidResetLinkException extends BusinessException {
    public InvalidResetLinkException() {
        super("El enlace no es válido o ya venció. Pide uno nuevo.", HttpStatus.BAD_REQUEST, "invalid-reset-link");
    }
}
