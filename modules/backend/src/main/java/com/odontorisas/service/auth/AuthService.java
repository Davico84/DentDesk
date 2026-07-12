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
import com.odontorisas.persistence.entity.OrganizationSettings;
import com.odontorisas.persistence.entity.RefreshToken;
import com.odontorisas.persistence.entity.User;
import com.odontorisas.persistence.repository.MembershipRepository;
import com.odontorisas.persistence.repository.OrganizationRepository;
import com.odontorisas.persistence.repository.OrganizationSettingsRepository;
import com.odontorisas.persistence.repository.RefreshTokenRepository;
import com.odontorisas.persistence.repository.UserRepository;
import com.odontorisas.service.organization.OrganizationSlugService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * Casos de uso de autenticación sobre el modelo multi-tenant. El registro crea, de forma
 * atómica, la organización, su configuración, la identidad y la membresía de administrador.
 * Login y refresh autentican por identidad global y establecen la sesión sobre la organización
 * de la membresía, exigiendo que identidad, membresía y organización estén activas.
 *
 * <p>{@code @Transactional} a nivel de clase: todo acceso a {@code memberships} (bajo RLS) va
 * en transacción (design.md D12); antes de tocar membresías se fija la identidad en la sesión
 * de PostgreSQL con {@link TenantSession#bindIdentity(long)}.
 */
@Service
@Transactional
public class AuthService {

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final OrganizationSettingsRepository organizationSettings;
    private final MembershipRepository memberships;
    private final RefreshTokenRepository refreshTokens;
    private final OrganizationSlugService slugService;
    private final TenantSession tenantSession;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserRepository users,
                       OrganizationRepository organizations,
                       OrganizationSettingsRepository organizationSettings,
                       MembershipRepository memberships,
                       RefreshTokenRepository refreshTokens,
                       OrganizationSlugService slugService,
                       TenantSession tenantSession,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.users = users;
        this.organizations = organizations;
        this.organizationSettings = organizationSettings;
        this.memberships = memberships;
        this.refreshTokens = refreshTokens;
        this.slugService = slugService;
        this.tenantSession = tenantSession;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    /** Alta autoservicio: organización + configuración + identidad + membresía ADMIN, atómico. */
    public AuthResult register(RegisterCommand cmd) {
        if (users.existsByEmail(cmd.email())) {
            throw new EmailAlreadyExistsException(cmd.email());
        }
        Organization organization = organizations.save(Organization.builder()
            .name(cmd.organizationName())
            .slug(slugService.slugFor(cmd.organizationName()))
            .type(OrganizationType.CLINIC)
            .status(OrganizationStatus.ACTIVE)
            .build());
        organizationSettings.save(OrganizationSettings.defaultsFor(organization));

        User user = users.save(User.builder()
            .email(cmd.email())
            .passwordHash(passwordEncoder.encode(cmd.password()))
            .fullName(cmd.fullName())
            .status(UserStatus.ACTIVE)
            .build());

        // La RLS de identidad de memberships exige app.user_id fijado para el INSERT.
        tenantSession.bindIdentity(user.getId());
        Membership membership = memberships.save(Membership.builder()
            .user(user)
            .organization(organization)
            .role(Role.ADMIN)
            .status(MembershipStatus.ACTIVE)
            .build());

        return issueTokens(user, membership);
    }

    public AuthResult login(String email, String rawPassword) {
        User user = users.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        Membership membership = activeMembership(user, InvalidCredentialsException::new);
        return issueTokens(user, membership);
    }

    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        String tokenHash = tokenService.hashRefreshToken(rawRefreshToken);
        RefreshToken stored = refreshTokens.findByTokenHash(tokenHash)
            .orElseThrow(InvalidRefreshTokenException::new);
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }
        User user = stored.getUser();
        // Reevaluación del estado en cada renovación: suspender una membresía corta la
        // sesión en el siguiente refresh (ventana = TTL del access), no al expirar el refresh.
        Membership membership = activeMembership(user, InvalidRefreshTokenException::new);
        // Rotación atómica: consume el token con un bulk delete. Si otra petición concurrente
        // ya lo consumió (0 filas), esta pierde la carrera → 401 (no 500).
        if (refreshTokens.deleteByTokenHash(tokenHash) == 0) {
            throw new InvalidRefreshTokenException();
        }
        return issueTokens(user, membership);
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return; // idempotente
        }
        refreshTokens.findByTokenHash(tokenService.hashRefreshToken(rawRefreshToken))
            .ifPresent(refreshTokens::delete);
    }

    /**
     * Membresía activa del usuario para establecer sesión (gate por estado, design.md D14):
     * identidad, membresía y organización deben estar {@code ACTIVE}. En este alcance cada
     * usuario tiene una única membresía; el manejo de varias se difiere a la capacidad `users`.
     * No revela cuál de los tres estados falló ({@code onFail} decide el error visible).
     */
    private Membership activeMembership(User user, Supplier<? extends RuntimeException> onFail) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw onFail.get();
        }
        // Fija la identidad antes de leer memberships (RLS de identidad).
        tenantSession.bindIdentity(user.getId());
        return memberships.findByUserId(user.getId()).stream()
            .filter(m -> m.getStatus() == MembershipStatus.ACTIVE
                && m.getOrganization().getStatus() == OrganizationStatus.ACTIVE)
            .findFirst()
            .orElseThrow(onFail);
    }

    private AuthResult issueTokens(User user, Membership membership) {
        Organization organization = membership.getOrganization();
        String accessToken = tokenService.issueAccessToken(
            user.getId(), user.getEmail(), organization.getId(), membership.getRole());
        String rawRefresh = tokenService.generateRefreshTokenValue();
        Instant refreshExpiry = tokenService.refreshExpiry();
        refreshTokens.save(RefreshToken.builder()
            .user(user)
            .tokenHash(tokenService.hashRefreshToken(rawRefresh))
            .expiresAt(refreshExpiry)
            .revoked(false)
            .build());
        return new AuthResult(accessToken, rawRefresh, refreshExpiry, UserView.of(user, membership));
    }
}
