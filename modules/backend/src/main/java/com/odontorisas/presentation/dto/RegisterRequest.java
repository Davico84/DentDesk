package com.odontorisas.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de cuenta. Las restricciones de formato deben mantenerse en paridad con
 * el schema Zod del formulario de registro (ver docs/coding-style.md §7).
 */
public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres") String password
) {}
