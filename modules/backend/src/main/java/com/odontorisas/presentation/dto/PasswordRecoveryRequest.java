package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Pedido de recuperación de la contraseña ({@code update-authentication-password-recovery} design.md D1). Los
 * espacios de los extremos se quitan antes de validar: un correo con espacios es el mismo correo. Paridad de
 * formato con el {@code emailSchema} del login ({@code docs/coding-style.md} §7).
 */
public record PasswordRecoveryRequest(
    @Schema(description = "El correo de la cuenta. La respuesta es la misma exista o no.")
    @NotBlank @Email String email) {

    public PasswordRecoveryRequest {
        email = email == null ? null : email.trim();
    }
}
