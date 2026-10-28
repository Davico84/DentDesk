package com.odontorisas.service.auth;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** La contraseña nueva es igual a la actual (400, design.md D4 de {@code update-authentication-my-profile}). */
public class NewPasswordSameAsCurrentException extends BusinessException {
    public NewPasswordSameAsCurrentException() {
        super("La contraseña nueva debe ser distinta de la actual.", HttpStatus.BAD_REQUEST, "new-password-same-as-current");
    }
}
