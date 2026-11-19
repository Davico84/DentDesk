package com.odontorisas.infra.mail;

import com.odontorisas.common.Role;
import com.odontorisas.service.auth.AccountMailer.RecoveryMail;
import com.odontorisas.service.staff.InvitationDeliveryException;
import com.odontorisas.service.staff.InvitationMailer.InvitationMail;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * STARTTLS obligatorio por defecto y modo local sin STARTTLS ni AUTH
 * ({@code update-project-foundation-local-mail} spec "Envío de correo seguro por defecto" y "Modo de correo
 * local acotado a la máquina"), contra un servidor SMTP falso que registra los comandos.
 */
class SmtpSendersTest {

    private static final Instant EXPIRES = Instant.parse("2026-11-20T00:00:00Z");

    private static MailProperties properties(String host, int port, boolean localSmtp) {
        return new MailProperties(true, host, port, "usuario", "clave", "no-reply@dentdesk.test",
            "http://localhost:5173", Duration.ofSeconds(2), localSmtp);
    }

    private static InvitationMail invitation() {
        return new InvitationMail("ana@clinica.test", "Clínica", Role.MEMBER, "Ana", "tok", "123456", EXPIRES);
    }

    @Test
    void by_default_starttls_is_enabled_and_required_and_auth_is_on() {
        Properties javaMail = SmtpSenders.create(properties("smtp-relay.brevo.com", 587, false)).getJavaMailProperties();

        assertThat(javaMail.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
        assertThat(javaMail.getProperty("mail.smtp.starttls.required")).isEqualTo("true");
        assertThat(javaMail.getProperty("mail.smtp.auth")).isEqualTo("true");
    }

    @Test
    void local_mode_turns_off_starttls_and_auth() {
        Properties javaMail = SmtpSenders.create(properties("localhost", 1025, true)).getJavaMailProperties();

        assertThat(javaMail.getProperty("mail.smtp.starttls.enable")).isEqualTo("false");
        assertThat(javaMail.getProperty("mail.smtp.starttls.required")).isEqualTo("false");
        assertThat(javaMail.getProperty("mail.smtp.auth")).isEqualTo("false");
    }

    @Test
    void the_normal_mode_fails_before_data_against_a_server_without_starttls() throws Exception {
        try (FakeSmtpServer server = new FakeSmtpServer(false)) {
            var mailer = new SmtpInvitationMailer(properties("localhost", server.port(), false));

            assertThatThrownBy(() -> mailer.send(invitation())).isInstanceOf(InvitationDeliveryException.class);
            assertThat(server.commands()).doesNotContain("DATA", "MAIL");
        }
    }

    @Test
    void local_mode_delivers_an_invitation_without_starttls_or_auth() throws Exception {
        try (FakeSmtpServer server = new FakeSmtpServer(true)) {
            new SmtpInvitationMailer(properties("localhost", server.port(), true)).send(invitation());

            assertThat(server.commands()).contains("DATA").doesNotContain("STARTTLS", "AUTH");
        }
    }

    @Test
    void local_mode_delivers_a_recovery_link_without_starttls_or_auth() throws Exception {
        try (FakeSmtpServer server = new FakeSmtpServer(true)) {
            new SmtpAccountMailer(properties("localhost", server.port(), true))
                .sendRecoveryLink(new RecoveryMail("ana@clinica.test", "tok", EXPIRES));

            assertThat(server.commands()).contains("DATA").doesNotContain("STARTTLS", "AUTH");
        }
    }
}
