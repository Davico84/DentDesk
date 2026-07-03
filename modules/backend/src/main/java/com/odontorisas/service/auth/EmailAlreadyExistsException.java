package com.odontorisas.service.auth;

/** El correo ya está registrado (mapea a 409 Conflict). */
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("El correo ya está registrado: " + email);
    }
}
