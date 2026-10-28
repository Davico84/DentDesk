package com.odontorisas.service.auth;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** Cambio de contraseña con la cuenta bloqueada por intentos fallidos (429). No dice cuánto falta ({@code update-authentication-my-profile} design.md D4). */
public class AccountTemporarilyLockedException extends BusinessException {
    public AccountTemporarilyLockedException() {
        super("La cuenta está bloqueada temporalmente por intentos fallidos. Vuelve a intentarlo más tarde.",
            HttpStatus.TOO_MANY_REQUESTS, "account-temporarily-locked");
    }
}
