package com.odontorisas.infra.mail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Salvaguarda del modo de correo local ({@code update-project-foundation-local-mail} spec, design D2). */
class MailPropertiesTest {

    private static final String USER = "usuario-smtp-secreto";
    private static final String PASSWORD = "clave-smtp-secreta";

    private static MailProperties properties(boolean enabled, String host, boolean localSmtp) {
        return new MailProperties(enabled, host, 1025, USER, PASSWORD, "no-reply@dentdesk.test",
            "http://localhost:5173", Duration.ofSeconds(2), localSmtp);
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "LOCALHOST", "127.0.0.1", "::1", " localhost "})
    void local_mode_starts_with_a_local_host(String host) {
        assertThatCode(() -> properties(true, host, true)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"smtp-relay.brevo.com", "localhost.", "127.0.0.2", "[::1]", "0.0.0.0", "", " "})
    void local_mode_refuses_to_start_with_a_remote_or_ambiguous_host(String host) {
        assertThatThrownBy(() -> properties(true, host, true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void local_mode_refuses_to_start_without_a_host() {
        assertThatThrownBy(() -> properties(true, null, true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void the_startup_error_names_the_switch_and_the_host_but_no_credentials() {
        assertThatThrownBy(() -> properties(true, "smtp-relay.brevo.com", true))
            .hasMessageContaining("MAIL_LOCAL_SMTP")
            .hasMessageContaining("smtp-relay.brevo.com")
            .satisfies(error -> assertThat(error.getMessage()).doesNotContain(USER).doesNotContain(PASSWORD));
    }

    @Test
    void local_mode_with_mail_disabled_starts_with_any_host() {
        assertThatCode(() -> properties(false, "smtp-relay.brevo.com", true)).doesNotThrowAnyException();
    }

    @Test
    void the_normal_mode_accepts_a_remote_host() {
        assertThatCode(() -> properties(true, "smtp-relay.brevo.com", false)).doesNotThrowAnyException();
    }
}
