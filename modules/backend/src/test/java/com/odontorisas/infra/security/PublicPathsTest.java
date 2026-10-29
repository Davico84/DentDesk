package com.odontorisas.infra.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario "Info no es pública" (add-cold-start-warmup D2): de Actuator, solo health es público.
 */
class PublicPathsTest {

    @Test
    void only_health_is_public_from_actuator() {
        assertThat(PublicPaths.isPublic("/actuator/health")).isTrue();
        assertThat(PublicPaths.isPublic("/actuator/health/liveness")).isTrue();
        assertThat(PublicPaths.isPublic("/actuator/info")).isFalse();
        assertThat(PublicPaths.isPublic("/actuator/env")).isFalse();
    }

    @Test
    void under_auth_only_the_listed_routes_are_public() {
        for (String path : new String[] {"/auth/login", "/auth/register", "/auth/refresh", "/auth/logout",
            "/auth/invitations/validate", "/auth/invitations/verify",
            // update-authentication-password-recovery design.md D1: los tres sin sesión.
            "/auth/password-recovery", "/auth/password-reset", "/auth/password-reset/check"}) {
            assertThat(PublicPaths.isPublic(path)).as(path).isTrue();
        }
        assertThat(PublicPaths.isPublic("/auth/password")).isFalse();
        assertThat(PublicPaths.isPublic("/auth/password-reset/otra")).isFalse();
        // Un endpoint nuevo bajo /auth no nace público por omisión.
        assertThat(PublicPaths.isPublic("/auth/algo-nuevo")).isFalse();
    }
}
