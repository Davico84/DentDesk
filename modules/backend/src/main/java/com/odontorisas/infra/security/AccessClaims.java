package com.odontorisas.infra.security;

import com.odontorisas.common.Role;

/**
 * Datos extraídos de un access token válido. El {@code organizationId} es la organización
 * activa de la sesión (claim {@code oid}); el {@code role} es el de la membresía del usuario
 * en esa organización.
 */
public record AccessClaims(Long userId, Long organizationId, String email, Role role) {}
