package com.odontorisas.infra.mail;

import com.odontorisas.common.Role;
import com.odontorisas.service.staff.InvitationMailer.InvitationMail;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Texto del correo de invitación: marca Dentdesk, rol legible y firma según quién invita. */
class SmtpInvitationMailerTest {

    private static final String FRONTEND = "https://dentdeskapp.vercel.app/";
    private static final Instant EXPIRES = Instant.parse("2026-10-21T15:00:00Z");

    private static InvitationMail mail(Role role, String organization, String inviter) {
        return new InvitationMail("ana@clinica.test", organization, role, inviter, "tok en", "123456", EXPIRES);
    }

    @Test
    void the_admin_invitation_is_signed_by_dentdesk() {
        var content = SmtpInvitationMailer.compose(mail(Role.ADMIN, "Clínica Aurora", "Plataforma Dentdesk"), FRONTEND);

        assertThat(content.subject()).isEqualTo("Dentdesk: administra Clínica Aurora");
        assertThat(content.text()).startsWith("Dentdesk creó la cuenta de Clínica Aurora y te invita como Administrador.");
        assertThat(content.html()).contains("Clínica Aurora</strong>").contains("Administrador</strong>")
            .doesNotContain("Plataforma Dentdesk");
    }

    @Test
    void the_member_invitation_names_who_invites() {
        var content = SmtpInvitationMailer.compose(mail(Role.MEMBER, "Clínica Aurora", "Ana Admin"), FRONTEND);

        assertThat(content.subject()).isEqualTo("Clínica Aurora te invita a Dentdesk");
        assertThat(content.text()).startsWith("Ana Admin te invita a unirte a Clínica Aurora como Personal.");
        assertThat(content.html()).contains("Ana Admin</strong>").contains("Personal</strong>");
    }

    @Test
    void a_member_invitation_without_inviter_name_falls_back_to_the_clinic() {
        var content = SmtpInvitationMailer.compose(mail(Role.MEMBER, "Clínica Aurora", " "), FRONTEND);

        assertThat(content.text()).startsWith("Clínica Aurora te invita a unirte como Personal.");
    }

    @Test
    void the_brand_is_dentdesk_and_the_raw_role_never_shows() {
        var content = SmtpInvitationMailer.compose(mail(Role.ADMIN, "OdontoRisas", "x"), FRONTEND);

        assertThat(content.html()).contains("DENTDESK").contains("<title>Invitación a Dentdesk</title>")
            .doesNotContain(">ADMIN<").doesNotContain(" ADMIN.");
        assertThat(content.text()).doesNotContain(" ADMIN");
    }

    @Test
    void link_code_and_expiry_are_in_both_parts() {
        var content = SmtpInvitationMailer.compose(mail(Role.MEMBER, "Clínica Aurora", "Ana"), FRONTEND);
        String link = "https://dentdeskapp.vercel.app/auth/register?token=tok+en";

        assertThat(content.text()).contains(link).contains("123456").contains("2026-10-21");
        assertThat(content.html()).contains("href=\"" + link + "\"").contains("123456").contains("2026-10-21");
    }

    /** El nombre de la clínica y el de quien invita son datos de usuario: no pueden inyectar HTML. */
    @Test
    void user_supplied_names_are_escaped_in_the_html() {
        var content = SmtpInvitationMailer.compose(
            mail(Role.MEMBER, "<b>Clínica</b>", "Eva <script>alert(1)</script>"), FRONTEND);

        assertThat(content.html()).doesNotContain("<script>").doesNotContain("<b>Clínica</b>")
            .contains("&lt;script&gt;").contains("&lt;b&gt;Clínica&lt;/b&gt;");
    }
}
