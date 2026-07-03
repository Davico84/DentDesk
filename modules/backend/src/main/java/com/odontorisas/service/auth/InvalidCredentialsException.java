package com.odontorisas.service.auth;

/** Credenciales inválidas (mapea a 401; mensaje genérico para no filtrar la existencia de la cuenta). */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Correo electrónico o contraseña incorrectos");
    }
}
