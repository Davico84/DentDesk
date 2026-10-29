package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** ¿El enlace sigue vigente? El token va en el cuerpo, nunca en la URL de la API (design.md D1). */
public record PasswordResetCheckRequest(
    @Schema(description = "El token del enlace de recuperación.")
    @NotBlank String token) {}
