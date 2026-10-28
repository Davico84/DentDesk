package com.odontorisas.service.auth;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** Contraseña actual incorrecta al cambiarla (400). Mensaje propio, no el genérico del login: quien pide ya está autenticado, no hay enumeración que proteger ({@code update-authentication-my-profile} design.md D4). */
public class CurrentPasswordIncorrectException extends BusinessException {
    public CurrentPasswordIncorrectException() {
        super("La contraseña actual no es correcta.", HttpStatus.BAD_REQUEST, "current-password-incorrect");
    }
}
