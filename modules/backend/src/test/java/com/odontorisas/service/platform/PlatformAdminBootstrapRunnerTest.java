package com.odontorisas.service.platform;

import com.odontorisas.infra.security.PlatformBootstrapProperties;
import com.odontorisas.persistence.entity.PlatformAdmin;
import com.odontorisas.persistence.entity.User;
import com.odontorisas.persistence.repository.PlatformAdminRepository;
import com.odontorisas.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Bootstrap operativo del primer PLATFORM_ADMIN (add-platform-admin D2, tarea 1.3). */
@ExtendWith(MockitoExtension.class)
class PlatformAdminBootstrapRunnerTest {

    @Mock UserRepository users;
    @Mock PlatformAdminRepository platformAdmins;
    @Mock PasswordEncoder passwordEncoder;

    private PlatformAdminBootstrapRunner runner(boolean enabled, String email, String password) {
        return new PlatformAdminBootstrapRunner(
            new PlatformBootstrapProperties(enabled, email, "Plataforma", password),
            users, platformAdmins, passwordEncoder);
    }

    @Test
    void disabled_bootstrap_touches_nothing() {
        runner(false, "root@dentdesk.test", "secret-123").run(null);

        verifyNoInteractions(users, platformAdmins, passwordEncoder);
    }

    @Test
    void creates_identity_and_global_authorization_with_normalized_email() {
        when(users.findByEmail("root@dentdesk.test")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret-123")).thenReturn("hash");
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        runner(true, "  Root@Dentdesk.test ", "secret-123").run(null);

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users).save(user.capture());
        assertThat(user.getValue().getEmail()).isEqualTo("root@dentdesk.test");
        assertThat(user.getValue().getPasswordHash()).isEqualTo("hash");
        ArgumentCaptor<PlatformAdmin> admin = ArgumentCaptor.forClass(PlatformAdmin.class);
        verify(platformAdmins).save(admin.capture());
        assertThat(admin.getValue().getUser().getId()).isEqualTo(42L);
    }

    /** Re-arrancar con el bootstrap aún activo no duplica ni cambia la contraseña existente. */
    @Test
    void is_idempotent_when_the_identity_is_already_platform_admin() {
        User existing = User.builder().id(7L).email("root@dentdesk.test").passwordHash("old").build();
        when(users.findByEmail("root@dentdesk.test")).thenReturn(Optional.of(existing));
        when(platformAdmins.existsById(7L)).thenReturn(true);

        runner(true, "root@dentdesk.test", "otra-clave").run(null);

        verify(users, never()).save(any());
        verify(platformAdmins, never()).save(any());
        verifyNoInteractions(passwordEncoder);
        assertThat(existing.getPasswordHash()).isEqualTo("old");
    }

    /** Un correo de una identidad de clínica no se promueve a global por configuración. */
    @Test
    void fails_when_the_email_belongs_to_a_non_platform_identity() {
        User clinicUser = User.builder().id(9L).email("admin@clinica.test").build();
        when(users.findByEmail("admin@clinica.test")).thenReturn(Optional.of(clinicUser));
        when(platformAdmins.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> runner(true, "admin@clinica.test", "secret-123").run(null))
            .isInstanceOf(IllegalStateException.class);
        verify(platformAdmins, never()).save(any());
    }

    @Test
    void fails_when_enabled_without_password() {
        assertThatThrownBy(() -> runner(true, "root@dentdesk.test", "").run(null))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(users, platformAdmins);
    }
}
