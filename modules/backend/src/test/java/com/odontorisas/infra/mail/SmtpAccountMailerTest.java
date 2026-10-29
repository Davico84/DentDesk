package com.odontorisas.infra.mail;

import com.odontorisas.service.auth.AccountMailer.PasswordChangedMail;
import com.odontorisas.service.auth.AccountMailer.RecoveryMail;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Texto de los correos de la cuenta ({@code update-authentication-password-recovery} design.md D6). Scenarios:
 * <i>Aviso al restablecer</i>, <i>Qué hacer si no fue el titular</i>; requirement <i>Aviso por correo del cambio
 * de contraseña</i> (sin enlaces que cambien la contraseña).
 */
class SmtpAccountMailerTest {

    private static final String FRONTEND = "https://dentdeskapp.vercel.app/";

    @Test
    void the_recovery_link_points_to_the_frontend_screen_with_the_encoded_token() {
        var content = SmtpAccountMailer.composeRecovery(
            new RecoveryMail("ana@clinica.test", "tok+en/1", Instant.parse("2026-10-29T15:30:00Z")), FRONTEND);

        String link = "https://dentdeskapp.vercel.app/auth/reset-password?token=tok%2Ben%2F1";
        assertThat(content.subject()).isEqualTo("Dentdesk: recupera tu contraseña");
        assertThat(content.text()).contains(link).contains("30 minutos").contains("una sola vez");
        assertThat(content.html()).contains("href=\"" + link + "\"").doesNotContain("/api/");
    }

    @Test
    void the_recovery_mail_never_prints_the_token_in_its_description() {
        var mail = new RecoveryMail("ana@clinica.test", "secreto-del-enlace", Instant.parse("2026-10-29T15:30:00Z"));

        assertThat(mail.toString()).doesNotContain("secreto-del-enlace");
    }

    @Test
    void the_change_notice_has_the_peru_time_and_what_to_do_but_no_links() {
        // 20:05 UTC = 15:05 en Lima (UTC-5).
        var content = SmtpAccountMailer.composePasswordChanged(
            new PasswordChangedMail("ana@clinica.test", Instant.parse("2026-10-29T20:05:00Z")));

        assertThat(content.subject()).isEqualTo("Dentdesk: tu contraseña cambió");
        assertThat(content.text())
            .contains("29/10/2026 a las 15:05 (hora de Perú)")
            .contains("pide la recuperación de la contraseña")
            .contains("avisa al administrador de tu clínica");
        assertThat(content.html()).doesNotContain("href=").doesNotContain("http");
        assertThat(content.text()).doesNotContain("http");
    }
}
