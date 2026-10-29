package com.odontorisas.service.auth;

import com.odontorisas.common.MembershipStatus;
import com.odontorisas.common.OrganizationStatus;
import com.odontorisas.common.UserStatus;
import com.odontorisas.infra.security.TokenService;
import com.odontorisas.infra.tenancy.TenantSession;
import com.odontorisas.persistence.entity.PasswordResetToken;
import com.odontorisas.persistence.entity.User;
import com.odontorisas.persistence.repository.MembershipRepository;
import com.odontorisas.persistence.repository.PasswordResetTokenRepository;
import com.odontorisas.persistence.repository.PlatformAdminRepository;
import com.odontorisas.persistence.repository.RefreshTokenRepository;
import com.odontorisas.persistence.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Restablecer la contraseña con el enlace ({@code update-authentication-password-recovery} design.md D1, D5).
 *
 * <p><b>Orden de bloqueo {@code users} → {@code password_reset_tokens} → {@code refresh_tokens}</b>, el mismo que
 * el pedido ({@code users} → {@code password_reset_tokens}): pedido y restablecimiento simultáneos no forman
 * ciclo. Leer el token sin bloqueo en el paso 1 no cuenta como adquisición.
 *
 * <p>Corre en el hilo de la petición: para leer {@code memberships} (RLS de identidad) fija la identidad con
 * {@link TenantSession#bindIdentity}, como el login. Por eso el {@code @Transactional} va a nivel de clase.
 */
@Service
@Transactional
public class PasswordResetService {

    public enum Outcome { OK, LOCKED, ACCESS_DISABLED }

    /** {@code lockedUntil} solo con {@link Outcome#LOCKED}. */
    public record ResetResult(Outcome outcome, Instant lockedUntil) {}

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final PlatformAdminRepository platformAdmins;
    private final RefreshTokenRepository refreshTokens;
    private final MembershipRepository memberships;
    private final TenantSession tenantSession;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordChangeNotifier notifier;
    private final Clock clock;
    private AuthConcurrencyProbe probe = AuthConcurrencyProbe.NONE;

    public PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users,
                                PlatformAdminRepository platformAdmins, RefreshTokenRepository refreshTokens,
                                MembershipRepository memberships, TenantSession tenantSession,
                                TokenService tokenService, PasswordEncoder passwordEncoder,
                                PasswordChangeNotifier notifier, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.platformAdmins = platformAdmins;
        this.refreshTokens = refreshTokens;
        this.memberships = memberships;
        this.tenantSession = tenantSession;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** Solo los tests registran un probe; en producción queda {@code NONE}. */
    @Autowired(required = false)
    void setConcurrencyProbe(AuthConcurrencyProbe probe) {
        this.probe = probe;
    }

    /**
     * ¿El enlace sigue vigente? Para no hacer escribir una contraseña a quien abrió un enlace muerto (D1). No
     * revela nada que {@link #reset} no revele.
     *
     * @throws InvalidResetLinkException si no es vigente, sin decir por qué
     */
    @Transactional(readOnly = true)
    public void check(String rawToken) {
        PasswordResetToken token = tokens.findByTokenHash(tokenService.hashRefreshToken(rawToken))
            .orElseThrow(InvalidResetLinkException::new);
        if (!token.isValidAt(clock.instant()) || platformAdmins.existsByUserId(token.getUserId())) {
            throw new InvalidResetLinkException();
        }
    }

    /**
     * D5. La contraseña nueva ya llega validada por el DTO (un 400 de validación no llega acá, así que no
     * consume el enlace). Restablecer <b>no desbloquea</b>, <b>no reactiva</b> y <b>no abre sesión</b>: cierra
     * todas, también la de este navegador.
     *
     * @throws InvalidResetLinkException si el enlace no es vigente o la cuenta es de plataforma; no cambia nada
     */
    public ResetResult reset(String rawToken, String newPassword) {
        String tokenHash = tokenService.hashRefreshToken(rawToken);
        // 1. Sin bloqueo, solo para conocer al usuario.
        Long userId = tokens.findUserIdByTokenHash(tokenHash).orElseThrow(InvalidResetLinkException::new);
        // BCrypt antes de los bloqueos: no los retiene ~100 ms más de lo necesario.
        String newHash = passwordEncoder.encode(newPassword);
        probe.afterResetTokenRead();

        // 2. users primero, sin el filtro de "no bloqueado": restablecer con la cuenta bloqueada es válido.
        User user = users.findByIdForUpdate(userId).orElseThrow(InvalidResetLinkException::new);
        probe.afterResetUserLocked();

        // 3. El token, bajo su propio bloqueo: vigencia y que la cuenta no haya pasado a plataforma.
        PasswordResetToken token = tokens.findByTokenHashForUpdate(tokenHash)
            .orElseThrow(InvalidResetLinkException::new);
        Instant now = clock.instant();
        if (!token.isValidAt(now) || platformAdmins.existsByUserId(userId)) {
            throw new InvalidResetLinkException();
        }

        // 5. Hash nuevo; conserva un bloqueo vigente y su contador, si no lo hay los pone a cero.
        users.resetPassword(userId, newHash, now);
        // 6. Consumir este enlace y anular los demás vigentes.
        token.setUsedAt(now);
        tokens.revokeOtherValidOfUser(userId, token.getId(), now);
        // 7. Cerrar todas las sesiones; no se emite una nueva.
        refreshTokens.deleteAllOfUser(userId);

        ResetResult result = outcome(user, now);
        // 9. El aviso sale después del commit, por la cola de avisos.
        notifier.notifyAfterCommit(user.getEmail(), now);
        return result;
    }

    /**
     * 8. Qué puede hacer ahora la cuenta. {@code user} se leyó bajo el bloqueo y antes del {@code UPDATE} en
     * bloque, que no cambia {@code lockedUntil} si era futuro: refleja el bloqueo vigente.
     */
    private ResetResult outcome(User user, Instant now) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            return new ResetResult(Outcome.LOCKED, user.getLockedUntil());
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            return new ResetResult(Outcome.ACCESS_DISABLED, null);
        }
        // Una membresía activa en una clínica suspendida no da acceso, igual que en el login.
        tenantSession.bindIdentity(user.getId());
        boolean hasAccess = memberships.findByUserId(user.getId()).stream()
            .anyMatch(m -> m.getStatus() == MembershipStatus.ACTIVE
                && m.getOrganization().getStatus() == OrganizationStatus.ACTIVE);
        return new ResetResult(hasAccess ? Outcome.OK : Outcome.ACCESS_DISABLED, null);
    }
}
