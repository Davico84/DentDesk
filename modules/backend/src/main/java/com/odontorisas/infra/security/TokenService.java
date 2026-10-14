package com.odontorisas.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.odontorisas.common.Role;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Emisión y validación de tokens. El access token es un JWT HS256 (stateless);
 * el refresh token es un valor aleatorio opaco cuyo <em>hash</em> se persiste
 * (nunca el valor en claro).
 */
@Service
public class TokenService {

    private final SecurityProperties props;
    private final Algorithm algorithm;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(SecurityProperties props) {
        // Sin ninguna propiedad app.security.jwt.* el binder deja jwt() en null: se valida igual
        // (mensaje "obligatorio") en vez de fallar con un NullPointerException. Se valida acá y no
        // en el binding para que el error nunca imprima el secreto (ver JwtSecretRules).
        String secret = props.jwt() == null ? null : props.jwt().secret();
        JwtSecretRules.validate(secret);
        this.props = props;
        this.algorithm = Algorithm.HMAC256(secret);
    }

    /**
     * Emite un access token JWT firmado (HS256) con la identidad ({@code sub}, {@code email}),
     * la organización activa ({@code oid}) y el rol del usuario en ella ({@code role}).
     */
    public String issueAccessToken(Long userId, String email, Long organizationId, Role role) {
        Instant now = Instant.now();
        return JWT.create()
            .withIssuer(props.jwt().issuer())
            .withSubject(String.valueOf(userId))
            .withClaim("email", email)
            .withClaim("oid", organizationId)
            .withClaim("role", role.name())
            .withIssuedAt(now)
            .withExpiresAt(now.plus(props.jwt().accessTtl()))
            .sign(algorithm);
    }

    /** Token global sin organizacion activa para un administrador de plataforma. */
    public String issuePlatformAccessToken(Long userId, String email) {
        Instant now = Instant.now();
        return JWT.create()
            .withIssuer(props.jwt().issuer())
            .withSubject(String.valueOf(userId))
            .withClaim("email", email)
            .withClaim("authorities", List.of("ROLE_PLATFORM_ADMIN"))
            .withIssuedAt(now)
            .withExpiresAt(now.plus(props.jwt().accessTtl()))
            .sign(algorithm);
    }

    /**
     * Valida el access token y extrae sus claims. Lanza {@link JWTVerificationException} si es
     * inválido/expirado, o si carece de la organización activa ({@code oid}) — un token sin
     * organización no autentica ninguna petición (los tokens previos a multi-tenancy se rechazan).
     */
    public AccessClaims verifyAccessToken(String token) {
        DecodedJWT jwt = JWT.require(algorithm).withIssuer(props.jwt().issuer()).build().verify(token);
        Claim oid = jwt.getClaim("oid");
        List<String> authorities = jwt.getClaim("authorities").asList(String.class);
        boolean platformAdmin = authorities != null && authorities.contains("ROLE_PLATFORM_ADMIN");
        if (!platformAdmin && (oid.isNull() || oid.isMissing())) {
            throw new JWTVerificationException("El token no incluye la organización activa (oid).");
        }
        return new AccessClaims(
            Long.valueOf(jwt.getSubject()),
            platformAdmin ? null : oid.asLong(),
            jwt.getClaim("email").asString(),
            platformAdmin ? null : Role.valueOf(jwt.getClaim("role").asString()),
            platformAdmin);
    }

    /** Genera el valor en claro de un refresh token (opaco, aleatorio). */
    public String generateRefreshTokenValue() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hash SHA-256 (hex) del refresh token; es lo único que se persiste. */
    public String hashRefreshToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** Instante de expiración de un nuevo refresh token. */
    public Instant refreshExpiry() {
        return Instant.now().plus(props.jwt().refreshTtl());
    }
}
