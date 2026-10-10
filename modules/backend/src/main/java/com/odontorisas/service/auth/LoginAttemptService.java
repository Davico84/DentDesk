package com.odontorisas.service.auth;

import com.odontorisas.infra.security.LoginLockoutProperties;
import com.odontorisas.persistence.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Persiste el bloqueo por intentos fallidos en transacción PROPIA (design.md D7,
 * update-authentication): {@code REQUIRES_NEW} hace que el incremento commitee aunque
 * {@code AuthService.login} lance el 401 justo después — si viviera en la transacción de
 * {@code login}, el rollback del 401 borraría el incremento y el bloqueo nunca se activaría.
 */
@Service
public class LoginAttemptService {

    private final UserRepository users;
    private final Clock clock;
    private final LoginLockoutProperties lockoutProperties;

    public LoginAttemptService(UserRepository users, Clock clock, LoginLockoutProperties lockoutProperties) {
        this.users = users;
        this.clock = clock;
        this.lockoutProperties = lockoutProperties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId) {
        Instant now = clock.instant();
        Instant until = now.plus(lockoutProperties.window());
        users.recordFailedLogin(userId, now, lockoutProperties.maxAttempts(), until);
    }
}
