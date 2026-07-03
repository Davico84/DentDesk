package com.odontorisas.service.auth;

import com.odontorisas.common.Role;
import com.odontorisas.infra.security.TokenService;
import com.odontorisas.persistence.entity.RefreshToken;
import com.odontorisas.persistence.entity.User;
import com.odontorisas.persistence.repository.RefreshTokenRepository;
import com.odontorisas.persistence.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Casos de uso de autenticación: registro, login, renovación (con rotación del
 * refresh) y logout (revocación). El access token es stateless; el refresh se
 * persiste hasheado para poder rotarlo e invalidarlo de verdad.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserRepository users,
                       RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public AuthResult register(String email, String rawPassword) {
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        User user = users.save(User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(rawPassword))
            .role(Role.USER)
            .build());
        return issueTokens(user);
    }

    @Transactional
    public AuthResult login(String email, String rawPassword) {
        User user = users.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        RefreshToken stored = refreshTokens.findByTokenHash(tokenService.hashRefreshToken(rawRefreshToken))
            .orElseThrow(InvalidRefreshTokenException::new);
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }
        // Rotación: el refresh usado se elimina y se emite uno nuevo.
        User user = stored.getUser();
        refreshTokens.delete(stored);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return; // idempotente
        }
        refreshTokens.findByTokenHash(tokenService.hashRefreshToken(rawRefreshToken))
            .ifPresent(refreshTokens::delete);
    }

    private AuthResult issueTokens(User user) {
        String accessToken = tokenService.issueAccessToken(user);
        String rawRefresh = tokenService.generateRefreshTokenValue();
        Instant refreshExpiry = tokenService.refreshExpiry();
        refreshTokens.save(RefreshToken.builder()
            .user(user)
            .tokenHash(tokenService.hashRefreshToken(rawRefresh))
            .expiresAt(refreshExpiry)
            .revoked(false)
            .build());
        return new AuthResult(accessToken, rawRefresh, refreshExpiry,
            new UserView(user.getId(), user.getEmail(), user.getRole()));
    }
}
