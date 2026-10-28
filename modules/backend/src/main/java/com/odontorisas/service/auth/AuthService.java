package com.odontorisas.service.auth;

import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.MembershipStatus;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.OrganizationStatus;
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
import com.odontorisas.persistence.repository.RefreshTokenRepository;
import com.odontorisas.persistence.repository.UserRepository;
import com.odontorisas.service.organization.OrganizationSlugService;
import com.odontorisas.service.staff.StaffService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
    private final MembershipRepository memberships;
    private final RefreshTokenRepository refreshTokens;
    private final TenantSession tenantSession;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttemptService;
    private final Clock clock;
    private final StaffService staffService;
    private AuthConcurrencyProbe probe = AuthConcurrencyProbe.NONE;

    public AuthService(UserRepository users,
                       MembershipRepository memberships,
                       RefreshTokenRepository refreshTokens,
                       TenantSession tenantSession,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       LoginAttemptService loginAttemptService,
                       Clock clock,
                       StaffService staffService) {
        this.users = users;
        this.memberships = memberships;
        this.refreshTokens = refreshTokens;
        this.tenantSession = tenantSession;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.loginAttemptService = loginAttemptService;
        this.clock = clock;
        this.staffService = staffService;
    }

    /** Solo los tests de concurrencia registran un probe (design.md D3); en producción queda {@code NONE}. */
    @Autowired(required = false)
    void setConcurrencyProbe(AuthConcurrencyProbe probe) {
        this.probe = probe;
    }

    /** Alta autoservicio: organización + configuración + identidad + membresía ADMIN, atómico. */
    public AuthResult register(RegisterCommand cmd) {
        if (users.existsByEmail(cmd.email())) {
            throw new EmailAlreadyExistsException(cmd.email());
        }
        var invitation = staffService.consume(cmd.token(), cmd.email());

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
            .organization(invitation.getOrganization())
            .role(invitation.getRole())
            .status(MembershipStatus.ACTIVE)
            .build());

        return issueTokens(user, membership);
    }

    /**
     * Bloqueo por intentos fallidos (design.md, update-authentication): se comprueba ANTES de
     * verificar la contraseña (D2) para no gastar BCrypt en una cuenta ya bloqueada; una
     * identidad bloqueada se rechaza sin tocar el contador (D8, "insistir no prolonga la
     * ventana"). Solo un fallo de CONTRASEÑA cuenta (D8): se persiste en transacción propia
     * ({@link LoginAttemptService}) para sobrevivir al 401 que este método lanza a continuación
     * (D7). Un rechazo por estado ({@link #activeMembership}) NO incrementa ni resetea el
     * contador; el reset por éxito vive en ESTA transacción (no en la del colaborador) para que
     * un fallo posterior en {@code activeMembership}/{@code issueTokens} lo revierta con el resto
     * (D7, asimetría deliberada con el incremento). El reset es además CONDICIONAL (D13): entre
     * la lectura de {@code lockedUntil} de arriba y este punto, otra request concurrente puede
     * haber bloqueado la cuenta (BCrypt tarda ~100ms, ventana real); si el UPDATE condicional no
     * afecta ninguna fila, la cuenta se bloqueó en el medio y el login se rechaza sin emitir
     * tokens, en vez de ignorar ese bloqueo recién creado.
     */
    public AuthResult login(String email, String rawPassword) {
        User user = users.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        Instant now = clock.instant();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            loginAttemptService.recordFailure(user.getId());
            throw new InvalidCredentialsException();
        }
        Membership membership = activeMembership(user, InvalidCredentialsException::new);
        probe.afterLoginPasswordVerified();
        // Exige además el hash verificado: si un cambio de contraseña ganó en el medio, 0 filas y no hay
        // sesión con la contraseña anterior (update-authentication-my-profile design.md D3.2).
        if (users.resetLoginAttemptsIfNotLocked(user.getId(), user.getPasswordHash(), now) == 0) {
            throw new InvalidCredentialsException();
        }
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
        // FOR SHARE de la fila del usuario ANTES de consumir el token: serializa con un cambio de contraseña
        // (FOR UPDATE), que si va primero ya borró este token (update-authentication-my-profile design.md D3.3).
        users.lockForShare(user.getId());
        // Rotación atómica: consume el token con un bulk delete. Si otra petición concurrente
        // ya lo consumió (0 filas), esta pierde la carrera → 401 (no 500).
        if (refreshTokens.deleteByTokenHash(tokenHash) == 0) {
            throw new InvalidRefreshTokenException();
        }
        probe.afterRefreshTokenConsumed();
        return issueTokens(user, membership);
    }

    /**
     * Cambia la contraseña propia y cierra las otras sesiones ({@code update-authentication-my-profile}
     * design.md D2–D4). El usuario y la clínica salen de la sesión (token verificado), nunca de la petición.
     *
     * <p><b>Protocolo de bloqueo (D3.1):</b> se verifica <b>sin bloqueo</b> y se bloquea después. Es
     * obligatorio: {@link LoginAttemptService#recordFailure} corre en {@code REQUIRES_NEW} y actualiza la
     * misma fila; con el {@code FOR UPDATE} ya tomado esperaría a esta transacción, que espera a aquella, y
     * Postgres no lo detecta (el ciclo pasa por el hilo de Java). Bajo el bloqueo se relee el hash: si ya no
     * es el verificado, otro cambio ganó, y se rechaza <b>sin</b> contar el fallo (la contraseña era correcta
     * al verificarla).
     *
     * <p>Los tokens nuevos usan la membresía de la clínica de la sesión, no la primera activa: un usuario
     * puede tener varias. El rol sale de esa membresía, así que un rol cambiado mientras tanto queda al día.
     *
     * @throws AccountTemporarilyLockedException si la cuenta está bloqueada (antes de comparar, o en el medio)
     * @throws CurrentPasswordIncorrectException si la actual no coincide (cuenta como fallo) o ya cambió
     * @throws NewPasswordSameAsCurrentException si la nueva es igual a la actual
     * @throws InvalidRefreshTokenException      si la identidad o la membresía de la sesión ya no están activas
     */
    public AuthResult changePassword(String currentPassword, String newPassword) {
        Long userId = IdentityContext.get();
        Long organizationId = OrganizationContext.get();
        if (userId == null || organizationId == null) {
            throw new InvalidRefreshTokenException();
        }
        User user = users.findById(userId).orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new AccountTemporarilyLockedException();
        }
        String verifiedHash = user.getPasswordHash();
        if (!passwordEncoder.matches(currentPassword, verifiedHash)) {
            loginAttemptService.recordFailure(userId); // sin bloqueo tomado: ver el Javadoc
            throw new CurrentPasswordIncorrectException();
        }
        if (passwordEncoder.matches(newPassword, verifiedHash)) {
            throw new NewPasswordSameAsCurrentException();
        }
        // BCrypt antes del bloqueo: no lo retiene ~100 ms más de lo necesario.
        String newHash = passwordEncoder.encode(newPassword);
        probe.afterChangePasswordVerified();

        String lockedHash = users.lockPasswordHashIfNotLocked(userId, now)
            .orElseThrow(AccountTemporarilyLockedException::new);
        if (!lockedHash.equals(verifiedHash)) {
            throw new CurrentPasswordIncorrectException();
        }
        probe.afterChangePasswordLocked();
        Membership membership = activeMembershipIn(user, organizationId);
        users.changePassword(userId, newHash, now);
        refreshTokens.deleteAllOfUser(userId);
        // `user` quedó con el hash viejo (bulk update): issueTokens solo lee id, correo y nombre, que no cambian.
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

    /** Como {@link #activeMembership}, pero en la clínica de la sesión: no la primera activa. */
    private Membership activeMembershipIn(User user, Long organizationId) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidRefreshTokenException();
        }
        tenantSession.bindIdentity(user.getId());
        return memberships.findByUserId(user.getId()).stream()
            .filter(m -> m.getOrganization().getId().equals(organizationId)
                && m.getStatus() == MembershipStatus.ACTIVE
                && m.getOrganization().getStatus() == OrganizationStatus.ACTIVE)
            .findFirst()
            .orElseThrow(InvalidRefreshTokenException::new);
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
