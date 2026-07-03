package com.odontorisas.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta de cuenta con datos básicos de perfil. Las restricciones de formato deben
 * mantenerse en paridad con el schema Zod del formulario (docs/coding-style.md §7).
 */
public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres") String password,
    @NotBlank @Size(min = 2, max = 120) String fullName,
    @NotBlank @Size(min = 3, max = 30)
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Solo letras, números y . _ -") String username,
    @NotBlank @Pattern(regexp = "^[+0-9 ()-]{7,20}$", message = "Teléfono no válido") String phone,
    @NotBlank @Size(max = 56) String country
) {}
