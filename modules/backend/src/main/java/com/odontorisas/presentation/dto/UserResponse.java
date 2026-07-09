package com.odontorisas.presentation.dto;

import com.odontorisas.common.Role;
import com.odontorisas.service.auth.UserView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Datos públicos del usuario autenticado, incluida su organización activa y el rol que tiene
 * en ella (con los que el frontend muestra la clínica activa y evalúa el acceso por rol).
 */
public record UserResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fullName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Role role,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long organizationId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String organizationName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String organizationSlug) {

    public static UserResponse from(UserView user) {
        return new UserResponse(
            user.id(),
            user.email(),
            user.fullName(),
            user.role(),
            user.organizationId(),
            user.organizationName(),
            user.organizationSlug());
    }
}
