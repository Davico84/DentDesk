package com.odontorisas.infra.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario "Cookie de refresh acotada a la base" (add-cloud-deployment D1): el path de la cookie es
 * la base de la API normalizada + {@code /auth}, igual para la de alta y la de borrado.
 */
class RefreshCookieFactoryTest {

    @ParameterizedTest
    @CsvSource(value = {"'',/auth", "/api,/api/auth", "/api/,/api/auth", "api,/api/auth", "NULL,/auth"}, nullValues = "NULL")
    void cookie_path_is_the_normalized_base_plus_auth(String contextPath, String expected) {
        assertThat(RefreshCookieFactory.cookiePath(contextPath)).isEqualTo(expected);
    }

    @Test
    void create_and_clear_share_the_same_path() {
        SecurityProperties props = new SecurityProperties(
            new SecurityProperties.Jwt("x".repeat(40), "odontorisas", Duration.ofMinutes(15), Duration.ofDays(7)),
            new SecurityProperties.Cookie("refresh_token", true),
            new SecurityProperties.Cors(java.util.List.of("http://localhost:5173")));
        RefreshCookieFactory factory = new RefreshCookieFactory(props, "/api");

        ResponseCookie created = factory.create("token", Instant.now().plusSeconds(60));
        ResponseCookie cleared = factory.clear();

        assertThat(created.getPath()).isEqualTo("/api/auth");
        assertThat(cleared.getPath()).isEqualTo("/api/auth");
        assertThat(cleared.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(created.isHttpOnly()).isTrue();
        assertThat(created.getSameSite()).isEqualTo("Lax");
    }
}
