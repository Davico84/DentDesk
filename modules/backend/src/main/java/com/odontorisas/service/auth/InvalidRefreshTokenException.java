package com.odontorisas.service.auth;

/** El refresh token es inválido, expiró o fue revocado (mapea a 401 → fuerza logout en el cliente). */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Sesión expirada, vuelve a iniciar sesión");
    }
}
