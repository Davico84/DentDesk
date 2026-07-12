package com.odontorisas.service.auth;

import com.odontorisas.common.Role;
import com.odontorisas.persistence.entity.Membership;
import com.odontorisas.persistence.entity.User;

/**
 * Vista del usuario autenticado que sale del servicio hacia la presentación (sin datos
 * sensibles). El {@code role} y la organización activa provienen de la membresía con la que
 * se estableció la sesión.
 */
public record UserView(
    Long id,
    String email,
    String fullName,
    Role role,
    Long organizationId,
    String organizationName,
    String organizationSlug) {

    /** Compone la vista a partir de la identidad y la membresía activa. */
    public static UserView of(User user, Membership membership) {
        return new UserView(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            membership.getRole(),
            membership.getOrganization().getId(),
            membership.getOrganization().getName(),
            membership.getOrganization().getSlug());
    }
}
