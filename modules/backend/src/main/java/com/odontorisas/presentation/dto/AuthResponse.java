package com.odontorisas.presentation.dto;

import com.odontorisas.service.auth.AuthResult;

/**
 * Respuesta de autenticación: el access token (que el cliente guarda en memoria)
 * y el usuario. El refresh token NO va aquí: viaja en una cookie HttpOnly.
 */
public record AuthResponse(String accessToken, UserResponse user) {

    public static AuthResponse from(AuthResult result) {
        return new AuthResponse(result.accessToken(), UserResponse.from(result.user()));
    }
}
