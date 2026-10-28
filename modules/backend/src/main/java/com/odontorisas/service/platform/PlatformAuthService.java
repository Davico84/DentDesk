package com.odontorisas.service.platform;

import com.odontorisas.common.UserStatus;
import com.odontorisas.infra.security.TokenService;
import com.odontorisas.persistence.entity.User;
import com.odontorisas.persistence.repository.PlatformAdminRepository;
import com.odontorisas.persistence.repository.UserRepository;
import com.odontorisas.service.auth.InvalidCredentialsException;
import com.odontorisas.service.auth.LoginAttemptService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Login global. Aplica el mismo bloqueo por intentos fallidos que el login de clínica
 * ({@code AuthService.login}): es la cuenta con más privilegios del sistema.
 */
@Service
@Transactional
public class PlatformAuthService {
    private final UserRepository users;
    private final PlatformAdminRepository platformAdmins;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttemptService;
    private final Clock clock;

    public PlatformAuthService(UserRepository users, PlatformAdminRepository platformAdmins,
                               PasswordEncoder passwordEncoder, TokenService tokenService,
                               LoginAttemptService loginAttemptService, Clock clock) {
        this.users = users;
        this.platformAdmins = platformAdmins;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.loginAttemptService = loginAttemptService;
        this.clock = clock;
    }

    public String login(String email, String password) {
        User user = users.findByEmail(email.trim().toLowerCase())
            .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
            .filter(candidate -> platformAdmins.existsById(candidate.getId()))
            .orElseThrow(InvalidCredentialsException::new);
        Instant now = clock.instant();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptService.recordFailure(user.getId());
            throw new InvalidCredentialsException();
        }
        if (users.resetLoginAttemptsIfNotLocked(user.getId(), user.getPasswordHash(), now) == 0) {
            throw new InvalidCredentialsException();
        }
        return tokenService.issuePlatformAccessToken(user.getId(), user.getEmail());
    }
}
