package com.odontorisas.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.odontorisas.common.Role;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests del access token multi-tenant: el claim de organización ({@code oid}) va en el token y es
 * obligatorio al validar (un token sin organización activa no autentica — comportamiento BREAKING
 * de multi-tenancy: los tokens previos se rechazan).
 */
class TokenServiceTest {

    private static final String SECRET = "un-secreto-de-test-suficientemente-largo-1234567890";

    private final SecurityProperties props = new SecurityProperties(
        new SecurityProperties.Jwt(SECRET, "odontorisas", Duration.ofMinutes(15), Duration.ofDays(7)),
        new SecurityProperties.Cookie("refresh_token", true),
        new SecurityProperties.Cors(java.util.List.of("http://localhost:5173")));

    private final TokenService tokenService = new TokenService(props);

    @Test
    void issued_token_carries_identity_organization_and_role() {
        String token = tokenService.issueAccessToken(7L, "ana@clinica.test", 42L, Role.ADMIN);

        AccessClaims claims = tokenService.verifyAccessToken(token);

        assertThat(claims.userId()).isEqualTo(7L);
        assertThat(claims.organizationId()).isEqualTo(42L);
        assertThat(claims.email()).isEqualTo("ana@clinica.test");
        assertThat(claims.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void a_token_without_organization_is_rejected() {
        // Token válido en firma pero SIN el claim oid (equivale a un token previo a multi-tenancy).
        Instant now = Instant.now();
        String legacyToken = JWT.create()
            .withIssuer("odontorisas")
            .withSubject("7")
            .withClaim("email", "ana@clinica.test")
            .withClaim("role", "ADMIN")
            .withIssuedAt(now)
            .withExpiresAt(now.plus(Duration.ofMinutes(15)))
            .sign(Algorithm.HMAC256(SECRET));

        assertThatThrownBy(() -> tokenService.verifyAccessToken(legacyToken))
            .isInstanceOf(JWTVerificationException.class);
    }
}
