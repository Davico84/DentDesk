package com.odontorisas.presentation.dto;

import com.odontorisas.service.auth.ProfileService.ProfileView;
import io.swagger.v3.oas.annotations.media.Schema;

/** Perfil propio: el nombre (editable) y el correo (solo de lectura). */
public record ProfileResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fullName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email) {

    public static ProfileResponse from(ProfileView view) {
        return new ProfileResponse(view.fullName(), view.email());
    }
}
