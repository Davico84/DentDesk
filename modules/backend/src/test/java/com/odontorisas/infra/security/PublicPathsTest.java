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
    void changing_the_password_is_the_only_authenticated_route_under_auth() {
        assertThat(PublicPaths.isPublic("/auth/login")).isTrue();
        assertThat(PublicPaths.isPublic("/auth/refresh")).isTrue();
        assertThat(PublicPaths.isPublic("/auth/password")).isFalse();
    }
}
