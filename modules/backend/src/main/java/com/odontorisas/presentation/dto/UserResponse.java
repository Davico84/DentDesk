package com.odontorisas.presentation.dto;

import com.odontorisas.common.Role;
import com.odontorisas.service.auth.UserView;

/** Datos públicos del usuario autenticado. */
public record UserResponse(Long id, String email, Role role) {

    public static UserResponse from(UserView user) {
        return new UserResponse(user.id(), user.email(), user.role());
    }
}
