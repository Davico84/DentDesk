package com.odontorisas.service.auth;

import com.odontorisas.common.MembershipStatus;
import com.odontorisas.common.OrganizationStatus;
import com.odontorisas.common.OrganizationType;
import com.odontorisas.common.Role;
import com.odontorisas.common.UserStatus;
import com.odontorisas.infra.security.TokenService;
import com.odontorisas.infra.tenancy.TenantSession;
import com.odontorisas.persistence.entity.Membership;
import com.odontorisas.persistence.entity.Organization;
import com.odontorisas.persistence.entity.RefreshToken;
import com.odontorisas.persistence.entity.User;
import com.odontorisas.persistence.repository.MembershipRepository;
import com.odontorisas.persistence.repository.OrganizationRepository;
import com.odontorisas.persistence.repository.OrganizationSettingsRepository;
import com.odontorisas.persistence.repository.RefreshTokenRepository;
import com.odontorisas.persistence.repository.UserRepository;
import com.odontorisas.service.organization.OrganizationSlugService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de AuthService (sin Spring ni base de datos) — cubren los Scenario de la
 * autenticación multi-tenant a nivel de lógica: alta atómica de organización+identidad+membresía,
 * login/refresh por identidad con gate por estado (identidad/membresía/organización activas).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock OrganizationRepository organizations;
    @Mock OrganizationSettingsRepository organizationSettings;
    @Mock MembershipRepository memberships;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock OrganizationSlugService slugService;
    @Mock TenantSession tenantSession;
    @Mock PasswordEncoder passwordEncoder;
    @Mock TokenService tokenService;
    @Mock LoginAttemptService loginAttemptService;
    @Mock Clock clock;

    @InjectMocks AuthService authService;

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");

    /** Default: el reset condicional (D13) "gana la carrera" salvo que un test diga lo contrario. */
    @BeforeEach
    void defaultResetSucceeds() {
        lenient().when(users.resetLoginAttemptsIfNotLocked(anyLong(), any())).thenReturn(1);
    }

    // --- Factorías ---

    private User activeUser() {
        return User.builder().id(1L).email("ana@clinica.test").passwordHash("hashed")
            .fullName("Ana Pérez").status(UserStatus.ACTIVE).build();
    }

    private Organization activeOrg() {
        return Organization.builder().id(5L).name("Clínica Sonrisas").slug("clinica-sonrisas")
            .type(OrganizationType.CLINIC).status(OrganizationStatus.ACTIVE).build();
    }

    private Membership membership(User user, Organization org, MembershipStatus status) {
        return Membership.builder().id(9L).user(user).organization(org)
            .role(Role.ADMIN).status(status).build();
    }

    private RegisterCommand cmd(String email) {
        return new RegisterCommand(email, "password123", "Ana Pérez", "Clínica Sonrisas");
    }

    private void stubTokenIssuance() {
        when(tokenService.issueAccessToken(anyLong(), anyString(), anyLong(), any(Role.class)))
            .thenReturn("access-token");
        when(tokenService.generateRefreshTokenValue()).thenReturn("raw-refresh");
        when(tokenService.hashRefreshToken(anyString())).thenReturn("refresh-hash");
        when(tokenService.refreshExpiry()).thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));
    }

    // --- Registro ---

    @Test
    void register_creates_organization_identity_and_admin_membership() {
        when(users.existsByEmail("ana@clinica.test")).thenReturn(false);
        when(slugService.slugFor("Clínica Sonrisas")).thenReturn("clinica-sonrisas");
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(organizations.save(any())).thenAnswer(inv -> {
            Organization o = inv.getArgument(0);
            o.setId(5L);
            return o;
        });
        when(users.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(memberships.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubTokenIssuance();

        AuthResult result = authService.register(cmd("ana@clinica.test"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.user().email()).isEqualTo("ana@clinica.test");
        assertThat(result.user().role()).isEqualTo(Role.ADMIN);
        assertThat(result.user().organizationName()).isEqualTo("Clínica Sonrisas");
        assertThat(result.user().organizationSlug()).isEqualTo("clinica-sonrisas");
        verify(organizationSettings).save(any());
        verify(refreshTokens).save(any(RefreshToken.class));
        verify(tenantSession).bindIdentity(1L); // fija app.user_id antes del INSERT de membership
    }

    @Test
    void register_duplicate_email_is_rejected_without_creating() {
        when(users.existsByEmail("ana@clinica.test")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(cmd("ana@clinica.test")))
            .isInstanceOf(EmailAlreadyExistsException.class);

        verify(users, never()).save(any());
        verify(organizations, never()).save(any());
        verify(refreshTokens, never()).save(any());
    }

    // --- Login ---

    @Test
    void login_valid_credentials_establishes_session_on_membership_organization() {
        User user = activeUser();
        Organization org = activeOrg();
        when(users.findByEmail("ana@clinica.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(memberships.findByUserId(1L)).thenReturn(List.of(membership(user, org, MembershipStatus.ACTIVE)));
        stubTokenIssuance();

        AuthResult result = authService.login("ana@clinica.test", "password123");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.user().organizationId()).isEqualTo(5L);
        assertThat(result.user().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void login_unknown_email_fails_generically() {
        when(users.findByEmail("nadie@clinica.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nadie@clinica.test", "password123"))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_wrong_password_fails_generically() {
        when(users.findByEmail("ana@clinica.test")).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.matches("mala", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("ana@clinica.test", "mala"))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    // --- Bloqueo por intentos fallidos (design.md, update-authentication) ---

    @Test
    void login_wrong_password_records_the_failure_in_its_own_transaction() {
        when(users.findByEmail("ana@clinica.test")).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.matches("mala", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("ana@clinica.test", "mala"))
            .isInstanceOf(InvalidCredentialsException.class);

        // D7: el incremento lo hace el colaborador REQUIRES_NEW, no una escritura en esta transacción.
        verify(loginAttemptService).recordFailure(1L);
    }

    @Test
    void login_locked_identity_is_rejected_before_checking_the_password() {
        User locked = User.builder().id(1L).email("ana@clinica.test").passwordHash("hashed")
            .fullName("Ana Pérez").status(UserStatus.ACTIVE)
            .lockedUntil(NOW.plus(5, ChronoUnit.MINUTES)).build();
        when(users.findByEmail("ana@clinica.test")).thenReturn(Optional.of(locked));
        when(clock.instant()).thenReturn(NOW);

        assertThatThrownBy(() -> authService.login("ana@clinica.test", "password123"))
            .isInstanceOf(InvalidCredentialsException.class);

        // D2: rechaza antes de gastar BCrypt. D8: insistir durante el bloqueo no toca el contador.
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(loginAttemptService, never()).recordFailure(any());
    }

    @Test
    void login_with_expired_lock_and_correct_password_proceeds_normally() {
        User expiredLock = User.builder().id(1L).email("ana@clinica.test").passwordHash("hashed")
            .fullName("Ana Pérez").status(UserStatus.ACTIVE)
            .lockedUntil(NOW.minus(1, ChronoUnit.MINUTES)).build();
        Organization org = activeOrg();
        when(users.findByEmail("ana@clinica.test")).thenReturn(Optional.of(expiredLock));
        when(clock.instant()).thenReturn(NOW);
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(memberships.findByUserId(1L)).thenReturn(List.of(membership(expiredLock, org, MembershipStatus.ACTIVE)));
        stubTokenIssuance();

        AuthResult result = authService.login("ana@clinica.test", "password123");

        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(users).resetLoginAttemptsIfNotLocked(1L, NOW);
    }

    @Test
    void login_success_resets_the_attempt_counter_in_the_same_transaction() {
        User user = activeUser();
        Organization org = activeOrg();
        when(users.findByEmail("ana@clinica.test")).thenReturn(Optional.of(user));
        when(clock.instant()).thenReturn(NOW);
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(memberships.findByUserId(1L)).thenReturn(List.of(membership(user, org, MembershipStatus.ACTIVE)));
        stubTokenIssuance();

        authService.login("ana@clinica.test", "password123");

        // D7: el reset vive en la transacción de login (no en el colaborador REQUIRES_NEW),
        // para que un rechazo posterior por estado lo revierta junto con el resto.
        verify(users).resetLoginAttemptsIfNotLocked(1L, NOW);
        verify(loginAttemptService, never()).recordFailure(any());
    }

    @Test
    void login_loses_the_race_against_a_concurrent_lock_is_rejected_without_issuing_tokens() {
        User user = activeUser();
        Organization org = activeOrg();
        when(users.findByEmail("ana@clinica.test")).thenReturn(Optional.of(user));
        when(clock.instant()).thenReturn(NOW);
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(memberships.findByUserId(1L)).thenReturn(List.of(membership(user, org, MembershipStatus.ACTIVE)));
        // D13: otra request concurrente bloqueó la cuenta entre la lectura inicial de lockedUntil
        // y este punto (p. ej. un flood de intentos fallidos de alguien más) — el UPDATE
        // condicional afecta 0 filas.
        when(users.resetLoginAttemptsIfNotLocked(1L, NOW)).thenReturn(0);

        assertThatThrownBy(() -> authService.login("ana@clinica.test", "password123"))
            .isInstanceOf(InvalidCredentialsException.class);

        verify(refreshTokens, never()).save(any());
    }

    @Test
    void login_state_rejection_with_correct_password_does_not_touch_the_counter() {
        User disabled = User.builder().id(1L).email("ana@clinica.test").passwordHash("hashed")
            .fullName("Ana Pérez").status(UserStatus.DISABLED).build();
        when(users.findByEmail("ana@clinica.test")).thenReturn(Optional.of(disabled));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login("ana@clinica.test", "password123"))
            .isInstanceOf(InvalidCredentialsException.class);

        // D8: un rechazo por estado no incrementa NI reinicia — deja el contador como estaba.
        verify(loginAttemptService, never()).recordFailure(any());
        verify(users, never()).resetLoginAttemptsIfNotLocked(any(), any());
    }

    // --- Gate por estado (design.md D14) ---

    @Test
    void login_with_disabled_identity_is_rejected_generically() {
        User disabled = User.builder().id(1L).email("ana@clinica.test").passwordHash("hashed")
            .fullName("Ana Pérez").status(UserStatus.DISABLED).build();
        when(users.findByEmail("ana@clinica.test")).thenReturn(Optional.of(disabled));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login("ana@clinica.test", "password123"))
            .isInstanceOf(InvalidCredentialsException.class);
        verify(memberships, never()).findByUserId(any());
    }

    @Test
    void login_with_inactive_membership_is_rejected_generically() {
        User user = activeUser();
        Organization org = activeOrg();
        when(users.findByEmail("ana@clinica.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(memberships.findByUserId(1L)).thenReturn(List.of(membership(user, org, MembershipStatus.SUSPENDED)));

        assertThatThrownBy(() -> authService.login("ana@clinica.test", "password123"))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_with_suspended_organization_is_rejected_generically() {
        User user = activeUser();
        Organization suspended = Organization.builder().id(5L).name("Clínica Sonrisas").slug("clinica-sonrisas")
            .type(OrganizationType.CLINIC).status(OrganizationStatus.SUSPENDED).build();
        when(users.findByEmail("ana@clinica.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(memberships.findByUserId(1L)).thenReturn(List.of(membership(user, suspended, MembershipStatus.ACTIVE)));

        assertThatThrownBy(() -> authService.login("ana@clinica.test", "password123"))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    // --- Refresh ---

    @Test
    void refresh_valid_rotates_and_issues_new_session() {
        User user = activeUser();
        Organization org = activeOrg();
        RefreshToken stored = RefreshToken.builder()
            .id(10L).user(user).tokenHash("refresh-hash")
            .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).revoked(false).build();
        when(tokenService.hashRefreshToken("raw-refresh")).thenReturn("refresh-hash");
        when(refreshTokens.findByTokenHash("refresh-hash")).thenReturn(Optional.of(stored));
        when(memberships.findByUserId(1L)).thenReturn(List.of(membership(user, org, MembershipStatus.ACTIVE)));
        when(refreshTokens.deleteByTokenHash("refresh-hash")).thenReturn(1);
        when(tokenService.issueAccessToken(anyLong(), anyString(), anyLong(), any(Role.class)))
            .thenReturn("access-token");
        when(tokenService.generateRefreshTokenValue()).thenReturn("raw-refresh-2");
        when(tokenService.refreshExpiry()).thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));

        AuthResult result = authService.refresh("raw-refresh");

        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(refreshTokens).deleteByTokenHash("refresh-hash"); // rotación: consume el usado
        verify(refreshTokens).save(any(RefreshToken.class));     // emite uno nuevo
    }

    @Test
    void refresh_with_suspended_membership_closes_session() {
        User user = activeUser();
        Organization org = activeOrg();
        RefreshToken stored = RefreshToken.builder()
            .id(10L).user(user).tokenHash("refresh-hash")
            .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).revoked(false).build();
        when(tokenService.hashRefreshToken("raw-refresh")).thenReturn("refresh-hash");
        when(refreshTokens.findByTokenHash("refresh-hash")).thenReturn(Optional.of(stored));
        when(memberships.findByUserId(1L)).thenReturn(List.of(membership(user, org, MembershipStatus.SUSPENDED)));

        assertThatThrownBy(() -> authService.refresh("raw-refresh"))
            .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokens, never()).deleteByTokenHash(anyString());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void refresh_losing_a_concurrent_rotation_is_rejected_without_new_session() {
        User user = activeUser();
        Organization org = activeOrg();
        RefreshToken stored = RefreshToken.builder()
            .id(10L).user(user).tokenHash("refresh-hash")
            .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).revoked(false).build();
        when(tokenService.hashRefreshToken("raw-refresh")).thenReturn("refresh-hash");
        when(refreshTokens.findByTokenHash("refresh-hash")).thenReturn(Optional.of(stored));
        when(memberships.findByUserId(1L)).thenReturn(List.of(membership(user, org, MembershipStatus.ACTIVE)));
        when(refreshTokens.deleteByTokenHash("refresh-hash")).thenReturn(0); // otra petición ya lo consumió

        assertThatThrownBy(() -> authService.refresh("raw-refresh"))
            .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokens, never()).save(any());
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
            .id(11L).user(activeUser()).tokenHash("refresh-hash")
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
        RefreshToken stored = RefreshToken.builder().id(12L).user(activeUser()).tokenHash("refresh-hash").build();
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
