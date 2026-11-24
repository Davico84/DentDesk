package com.odontorisas.infra.mail;

import com.odontorisas.common.Role;
import com.odontorisas.service.staff.InvitationDeliveryException;
import com.odontorisas.service.staff.InvitationMailer.InvitationMail;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un servidor SMTP que no responde (como un puerto bloqueado por Render free) debe fallar dentro del
 * timeout con 503, no colgar la petición hasta que el proxy corte (502 a los 120 s, 2026-10-15).
 */
class SmtpInvitationMailerTimeoutTest {

    @Test
    void a_silent_smtp_server_fails_fast_with_a_delivery_error() throws Exception {
        try (ServerSocket silent = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                try (Socket ignored = silent.accept()) {
                    Thread.sleep(10_000); // acepta y nunca saluda
                } catch (Exception ignored) {
                    // el test cierra el socket al terminar
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();
            var mailer = new SmtpInvitationMailer(new MailProperties(true, "localhost", silent.getLocalPort(),
                "usuario", "clave", "no-reply@dentdesk.test", "http://localhost:5173", Duration.ofMillis(500), false));
            var mail = new InvitationMail("ana@clinica.test", "Clínica", Role.MEMBER, "Ana", "tok", "123456",
                Instant.parse("2026-10-21T00:00:00Z"));

            long start = System.nanoTime();
            assertThatThrownBy(() -> mailer.send(mail)).isInstanceOf(InvitationDeliveryException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        }
    }
}
