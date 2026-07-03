package com.odontorisas.service.auth;

import com.odontorisas.common.Role;
import com.odontorisas.infra.security.TokenService;
import com.odontorisas.persistence.entity.RefreshToken;
import com.odontorisas.persistence.entity.User;
import com.odontorisas.persistence.repository.RefreshTokenRepository;
import com.odontorisas.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de AuthService (sin Spring ni base de datos) — cubren los
 * Scenario de la capacidad authentication a nivel de lógica de negocio.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock PasswordEncoder passwordEncoder;
    @Mock TokenService tokenService;

    @InjectMocks AuthService authService;

    private User userWithId() {
        return User.builder().id(1L).email("ana@clinica.test").passwordHash("hashed").role(Role.USER).build();
    }

    private void stubTokenIssuance() {
        when(tokenService.issueAccessToken(any())).thenReturn("access-token");
        when(tokenService.generateRefreshTokenValue()).thenReturn("raw-refresh");
        when(tokenService.hashRefreshToken(anyString())).thenReturn("refresh-hash");
        when(tokenService.refreshExpiry()).thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));
    }

    // --- Registro ---

    @Test
    void register_success_creates_user_and_issues_session() {
        when(users.existsByEmail("ana@clinica.test")).thenReturn(false);
        when(users.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        stubTokenIssuance();

        AuthResult result = authService.register("ana@clinica.test", "password123");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("raw-refresh");
        assertThat(result.user().email()).isEqualTo("ana@clinica.test");
        assertThat(result.user().role()).isEqualTo(Role.USER);
        verify(refreshTokens).save(any(RefreshToken.class));
    }

    @Test
    void register_duplicate_email_is_rejected_without_creating() {
        when(users.existsByEmail("ana@clinica.test")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("ana@clinica.test", "password123"))
            .isInstanceOf(EmailAlreadyExistsException.class);

        verify(users, never()).save(any());
        verify(refreshTokens, never()).save(any());
    }

    // --- Login ---

    @Test
    void login_valid_credentials_establishes_session() {
        User user = userWithId();
        when(users.findByEmail("ana@clinica.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        stubTokenIssuance();

        AuthResult result = authService.login("ana@clinica.test", "password123");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.user().id()).isEqualTo(1L);
    }

    @Test
    void login_unknown_email_fails_generically() {
        when(users.findByEmail("nadie@clinica.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nadie@clinica.test", "password123"))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_wrong_password_fails_generically() {
        when(users.findByEmail("ana@clinica.test")).thenReturn(Optional.of(userWithId()));
        when(passwordEncoder.matches("mala", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("ana@clinica.test", "mala"))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    // --- Refresh ---

    @Test
    void refresh_valid_rotates_and_issues_new_session() {
        User user = userWithId();
        RefreshToken stored = RefreshToken.builder()
            .id(10L).user(user).tokenHash("refresh-hash")
            .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).revoked(false).build();
        when(tokenService.hashRefreshToken("raw-refresh")).thenReturn("refresh-hash");
        when(refreshTokens.findByTokenHash("refresh-hash")).thenReturn(Optional.of(stored));
        when(tokenService.issueAccessToken(any())).thenReturn("access-token");
        when(tokenService.generateRefreshTokenValue()).thenReturn("raw-refresh-2");
        when(tokenService.refreshExpiry()).thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));

        AuthResult result = authService.refresh("raw-refresh");

        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(refreshTokens).delete(stored);                 // rotación: revoca el usado
        verify(refreshTokens).save(any(RefreshToken.class));  // emite uno nuevo
    }

    @Test
    void refresh_unknown_token_is_rejected() {
        when(tokenService.hashRefreshToken("desconocido")).thenReturn("hash-x");
        when(refreshTokens.findByTokenHash("hash-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("desconocido"))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_expired_token_is_rejected() {
        RefreshToken expired = RefreshToken.builder()
            .id(11L).user(userWithId()).tokenHash("refresh-hash")
            .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)).revoked(false).build();
        when(tokenService.hashRefreshToken("raw-refresh")).thenReturn("refresh-hash");
        when(refreshTokens.findByTokenHash("refresh-hash")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("raw-refresh"))
            .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void refresh_blank_token_is_rejected() {
        assertThatThrownBy(() -> authService.refresh("  "))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    // --- Logout ---

    @Test
    void logout_revokes_stored_refresh_token() {
        RefreshToken stored = RefreshToken.builder().id(12L).user(userWithId()).tokenHash("refresh-hash").build();
        when(tokenService.hashRefreshToken("raw-refresh")).thenReturn("refresh-hash");
        when(refreshTokens.findByTokenHash("refresh-hash")).thenReturn(Optional.of(stored));

        authService.logout("raw-refresh");

        verify(refreshTokens).delete(stored);
    }

    @Test
    void logout_without_token_is_noop() {
        authService.logout(null);
        verify(refreshTokens, never()).delete(any());
    }
}
