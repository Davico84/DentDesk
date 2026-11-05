package com.odontorisas.service.auth;

import com.odontorisas.common.UserStatus;
import com.odontorisas.infra.security.PasswordRecoveryProperties;
import com.odontorisas.infra.security.TokenService;
import com.odontorisas.persistence.entity.PasswordResetToken;
import com.odontorisas.persistence.entity.User;
import com.odontorisas.persistence.repository.PasswordResetTokenRepository;
import com.odontorisas.persistence.repository.PlatformAdminRepository;
import com.odontorisas.persistence.repository.UserRepository;
import com.odontorisas.service.auth.AccountMailer.RecoveryMail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Lo que el pedido de recuperación hace <b>fuera de la petición</b>, en el executor de pedidos
 * ({@code update-authentication-password-recovery} design.md D3, D4). Así ni la respuesta ni su tiempo
 * dependen de la cuenta.
 *
 * <p>Corre sin contexto de tenant y solo toca tablas sin RLS ({@code users}, {@code platform_admins},
 * {@code password_reset_tokens}). No depende de {@code IdentityContext}, {@code OrganizationContext} ni de la
 * sesión de tenant: lo vigila {@code MultiTenancyArchitectureTest}.
 */
@Component
public class PasswordRecoveryWorker {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryWorker.class);

    private final UserRepository users;
    private final PlatformAdminRepository platformAdmins;
    private final PasswordResetTokenRepository tokens;
    private final TokenService tokenService;
    private final AccountMailer mailer;
    private final PasswordRecoveryProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private AuthConcurrencyProbe probe = AuthConcurrencyProbe.NONE;

    public PasswordRecoveryWorker(UserRepository users, PlatformAdminRepository platformAdmins,
                                  PasswordResetTokenRepository tokens, TokenService tokenService,
                                  AccountMailer mailer, PasswordRecoveryProperties properties, Clock clock,
                                  PlatformTransactionManager transactionManager) {
        this.users = users;
        this.platformAdmins = platformAdmins;
        this.tokens = tokens;
        this.tokenService = tokenService;
        this.mailer = mailer;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Solo los tests registran un probe; en producción queda {@code NONE}. */
    @Autowired(required = false)
    void setConcurrencyProbe(AuthConcurrencyProbe probe) {
        this.probe = probe;
    }

    /**
     * Emite el enlace si corresponde y, <b>después del commit</b>, lo envía: si la transacción falla, no llega un
     * enlace que no existe en la base. Nada se propaga: un fallo solo se registra, sin el token ni el enlace.
     */
    public void process(String normalizedEmail) {
        probe.beforeRecoveryProcessed();
        Optional<RecoveryMail> mail;
        try {
            mail = transaction.execute(status -> issue(normalizedEmail));
        } catch (RuntimeException ex) {
            log.error("No se pudo procesar un pedido de recuperación de contraseña.", ex);
            return;
        }
        if (mail == null || mail.isEmpty()) {
            return;
        }
        try {
            mailer.sendRecoveryLink(mail.get());
        } catch (RuntimeException ex) {
            log.warn("No se pudo enviar un enlace de recuperación: {}", ex.getMessage());
        }
    }

    /**
     * D3, en la transacción del worker. El usuario se bloquea con {@code FOR UPDATE} <b>antes</b> de comprobar
     * su estado: una desactivación (que actualiza la fila) o una promoción a plataforma (cuyo {@code INSERT} en
     * {@code platform_admins} toma {@code FOR KEY SHARE} sobre users) no se cuelan entre la comprobación y la
     * emisión. El bloqueo también serializa los pedidos simultáneos de la misma cuenta frente al límite.
     */
    Optional<RecoveryMail> issue(String normalizedEmail) {
        List<Long> ids = users.findIdsByNormalizedEmail(normalizedEmail);
        if (ids.size() != 1) {
            if (ids.size() > 1) {
                log.warn("Pedido de recuperación para un correo con más de una cuenta; no se envía nada.");
            }
            return Optional.empty();
        }
        Long userId = ids.get(0);
        probe.afterRecoveryUserFound();

        // Primera lectura del usuario en esta transacción (findIdsByNormalizedEmail no carga la entidad).
        User user = users.findByIdForUpdate(userId).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || platformAdmins.existsByUserId(userId)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Instant windowStart = now.minus(properties.linkWindow());
        if (tokens.countIssuedSince(userId, windowStart) >= properties.linksPerWindow()) {
            return Optional.empty();
        }
        tokens.revokeValidOfUser(userId, now);
        tokens.deleteIssuedBefore(userId, windowStart);
        String rawToken = tokenService.generateRefreshTokenValue();
        Instant expiresAt = now.plus(properties.linkTtl());
        tokens.save(PasswordResetToken.builder()
            .userId(userId)
            .tokenHash(tokenService.hashRefreshToken(rawToken))
            .createdAt(now)
            .expiresAt(expiresAt)
            .build());
        return Optional.of(new RecoveryMail(user.getEmail(), rawToken, expiresAt));
    }
}
