package com.odontorisas.service.platform;

import com.odontorisas.common.UserStatus;
import com.odontorisas.infra.security.PlatformBootstrapProperties;
import com.odontorisas.persistence.entity.PlatformAdmin;
import com.odontorisas.persistence.entity.User;
import com.odontorisas.persistence.repository.PlatformAdminRepository;
import com.odontorisas.persistence.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Crea una sola vez el administrador global cuando el entorno lo solicita explícitamente. */
@Component
public class PlatformAdminBootstrapRunner implements ApplicationRunner {
    private final PlatformBootstrapProperties properties;
    private final UserRepository users;
    private final PlatformAdminRepository platformAdmins;
    private final PasswordEncoder passwordEncoder;

    public PlatformAdminBootstrapRunner(PlatformBootstrapProperties properties, UserRepository users,
                                        PlatformAdminRepository platformAdmins,
                                        PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.platformAdmins = platformAdmins;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        validateConfiguration();
        String email = properties.email().trim().toLowerCase();
        User user = users.findByEmail(email).orElse(null);
        if (user != null) {
            if (platformAdmins.existsById(user.getId())) {
                return;
            }
            throw new IllegalStateException("El correo de bootstrap ya pertenece a otra identidad.");
        }
        user = users.save(User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(properties.password()))
            .fullName(properties.fullName().trim())
            .status(UserStatus.ACTIVE)
            .build());
        platformAdmins.save(PlatformAdmin.builder().user(user).build());
    }

    private void validateConfiguration() {
        if (properties.email() == null || properties.email().isBlank()
                || properties.password() == null || properties.password().isBlank()
                || properties.fullName() == null || properties.fullName().isBlank()) {
            throw new IllegalStateException(
                "El bootstrap de plataforma requiere email, full-name y password.");
        }
    }
}
