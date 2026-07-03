package com.odontorisas.service.auth;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** El nombre de usuario ya está en uso (409 Conflict). */
public class UsernameAlreadyExistsException extends BusinessException {
    public UsernameAlreadyExistsException(String username) {
        super("El nombre de usuario ya está en uso: " + username, HttpStatus.CONFLICT, "username-already-exists");
    }
}
