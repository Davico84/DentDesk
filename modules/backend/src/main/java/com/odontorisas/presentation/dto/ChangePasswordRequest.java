package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cambio de la contraseña propia ({@code update-authentication-my-profile} design.md D1). La nueva sigue la
 * regla del registro (mínimo 8, {@link RegisterRequest}); que sea distinta de la actual lo decide el
 * servicio, que es quien conoce el hash. La confirmación se valida solo en el cliente, como en el registro.
 * Paridad con el schema Zod del frontend ({@code docs/coding-style.md} §7).
 */
public record ChangePasswordRequest(
    @Schema(description = "La contraseña actual.")
    @NotBlank(message = "Ingresa tu contraseña actual.")
    String currentPassword,

    @Schema(description = "La contraseña nueva: al menos 8 caracteres, distinta de la actual.")
    @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    String newPassword) {}
