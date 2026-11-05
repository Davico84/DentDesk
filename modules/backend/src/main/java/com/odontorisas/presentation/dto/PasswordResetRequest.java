package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Restablecer la contraseña con el enlace ({@code update-authentication-password-recovery} design.md D1, D5).
 * La nueva sigue la regla del registro (mínimo 8); la confirmación se valida solo en el cliente, como en el
 * registro y en Mi perfil. Paridad con el schema Zod del frontend ({@code docs/coding-style.md} §7).
 */
public record PasswordResetRequest(
    @Schema(description = "El token del enlace de recuperación.")
    @NotBlank String token,

    @Schema(description = "La contraseña nueva: al menos 8 caracteres.")
    @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    String newPassword) {}
