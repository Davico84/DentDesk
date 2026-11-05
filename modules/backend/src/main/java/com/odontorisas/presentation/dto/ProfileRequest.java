package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corrección del perfil propio: solo el nombre. No hay campo de correo, así que un correo en el cuerpo se
 * ignora ({@code update-authentication-my-profile} design.md D1). El nombre se recorta <b>antes</b> de
 * validar, así que 2–120 cuenta sin los espacios de los extremos, que tampoco se guardan (D5). Paridad con
 * el schema Zod del frontend ({@code docs/coding-style.md} §7).
 */
public record ProfileRequest(
    @Schema(description = "Nombre completo, de 2 a 120 caracteres sin contar los espacios de los extremos.",
        example = "Dra. Ana Torres")
    @NotBlank(message = "El nombre es obligatorio.")
    @Size(min = 2, max = 120, message = "El nombre debe tener entre 2 y 120 caracteres.")
    String fullName) {

    public ProfileRequest {
        fullName = fullName == null ? null : fullName.strip();
    }
}
