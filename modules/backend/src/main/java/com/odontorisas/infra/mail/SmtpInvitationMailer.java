package com.odontorisas.infra.mail;

import com.odontorisas.common.Role;
import com.odontorisas.service.staff.InvitationDeliveryException;
import com.odontorisas.service.staff.InvitationMailer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Adaptador SMTP compatible con Brevo y otros proveedores. */
final class SmtpInvitationMailer implements InvitationMailer {
    /** Mismas etiquetas que la app (ROLE_LABEL del frontend): el invitado no debe ver el enum. */
    private static final Map<Role, String> ROLE_LABEL = Map.of(Role.ADMIN, "Administrador", Role.MEMBER, "Personal");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE
        .withZone(ZoneOffset.UTC);
    private final MailProperties properties;
    private final JavaMailSenderImpl sender;

    SmtpInvitationMailer(MailProperties properties) {
        this.properties = properties;
        this.sender = new JavaMailSenderImpl();
        sender.setHost(properties.host());
        sender.setPort(properties.port());
        sender.setUsername(properties.username());
        sender.setPassword(properties.password());
        sender.getJavaMailProperties().put("mail.smtp.auth", "true");
        sender.getJavaMailProperties().put("mail.smtp.starttls.enable", "true");
        sender.getJavaMailProperties().put("mail.smtp.starttls.required", "true");
        String timeoutMillis = String.valueOf(properties.timeout().toMillis());
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", timeoutMillis);
        sender.getJavaMailProperties().put("mail.smtp.timeout", timeoutMillis);
        sender.getJavaMailProperties().put("mail.smtp.writetimeout", timeoutMillis);
    }

    @Override
    public void send(InvitationMail invitation) {
        Content content = compose(invitation, properties.frontendUrl());
        try {
            MimeMessage message = sender.createMimeMessage();
            // El mensaje contiene una parte de texto y otra HTML; debe crearse en modo multipart.
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.from());
            helper.setTo(invitation.recipient());
            helper.setSubject(content.subject());
            helper.setText(content.text(), content.html());
            sender.send(message);
        } catch (MessagingException | MailException ex) {
            throw new InvitationDeliveryException(
                "No se pudo enviar la invitación por correo. Inténtalo de nuevo en unos minutos.", ex);
        }
    }

    record Content(String subject, String text, String html) {}

    /**
     * Arma el correo sin enviarlo. El texto depende de QUIÉN invita: una invitación {@code ADMIN} solo la
     * emite la plataforma al aprovisionar la clínica, así que la firma Dentdesk; una {@code MEMBER} la
     * emite el administrador de la clínica, que aparece por su nombre. La marca es siempre Dentdesk; la
     * clínica es el nombre de la organización.
     */
    static Content compose(InvitationMail invitation, String frontendUrl) {
        String link = frontendUrl.replaceAll("/$", "")
            + "/auth/register?token="
            + URLEncoder.encode(invitation.rawToken(), StandardCharsets.UTF_8);
        String organization = invitation.organizationName();
        String role = ROLE_LABEL.get(invitation.role());
        String expiry = DATE_FORMAT.format(invitation.expiresAt());
        boolean fromPlatform = invitation.role() == Role.ADMIN;
        boolean namedInviter = invitation.inviterName() != null && !invitation.inviterName().isBlank();
        String subject = fromPlatform
            ? "Dentdesk: administra " + organization
            : organization + " te invita a Dentdesk";
        String introText;
        String introHtml;
        if (fromPlatform) {
            introText = "Dentdesk creó la cuenta de " + organization + " y te invita como " + role + ".";
            introHtml = "Dentdesk creó la cuenta de " + strong(organization) + " y te invita como " + strong(role) + ".";
        } else if (namedInviter) {
            introText = invitation.inviterName() + " te invita a unirte a " + organization + " como " + role + ".";
            introHtml = strong(invitation.inviterName()) + " te invita a unirte a " + strong(organization)
                + " como " + strong(role) + ".";
        } else {
            introText = organization + " te invita a unirte como " + role + ".";
            introHtml = strong(organization) + " te invita a unirte como " + strong(role) + ".";
        }
        String text = introText + "\n\n"
            + "Completa tu registro aquí:\n" + link + "\n\n"
            + "Tu código de verificación es: " + invitation.verificationCode() + "\n"
            + "La invitación vence el " + expiry + ".\n"
            + "Si no esperabas este correo, puedes ignorarlo.";
        String html = """
            <!doctype html>
            <html lang="es">
              <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="x-apple-disable-message-reformatting">
                <title>Invitación a Dentdesk</title>
              </head>
              <body style="margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%" style="background-color:#f4f7fb;width:100%%;">
                  <tr><td align="center" style="padding:32px 12px;">
                    <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="600" style="width:100%%;max-width:600px;background-color:#ffffff;border:1px solid #e5eaf1;border-radius:16px;">
                      <tr><td style="padding:32px 28px 12px;text-align:center;">
                        <div style="display:inline-block;padding:10px 16px;border-radius:999px;background-color:#e8f5f1;color:#13795b;font-size:13px;font-weight:bold;letter-spacing:1px;">DENTDESK</div>
                      </td></tr>
                      <tr><td style="padding:12px 28px 28px;text-align:center;">
                        <h1 style="margin:0 0 14px;font-size:26px;line-height:1.25;color:#172033;">Te damos la bienvenida</h1>
                        <p style="margin:0;font-size:16px;line-height:1.6;color:#4b5565;">%s</p>
                      </td></tr>
                      <tr><td align="center" style="padding:0 28px 24px;">
                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:0 auto;background-color:#f4f7fb;border-radius:12px;">
                          <tr><td style="padding:16px 28px;text-align:center;">
                            <p style="margin:0 0 6px;font-size:13px;color:#667085;">Tu código de verificación</p>
                            <p style="margin:0;font-size:30px;line-height:1.3;font-weight:bold;letter-spacing:6px;color:#13795b;">%s</p>
                          </td></tr>
                        </table>
                      </td></tr>
                      <tr><td align="center" style="padding:0 28px 24px;">
                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:0 auto;">
                          <tr><td align="center" bgcolor="#13795b" style="border-radius:8px;">
                            <a href="%s" style="display:inline-block;padding:14px 26px;border:1px solid #13795b;border-radius:8px;color:#ffffff;font-size:16px;font-weight:bold;text-decoration:none;">Completar registro</a>
                          </td></tr>
                        </table>
                      </td></tr>
                      <tr><td style="padding:0 28px 28px;text-align:center;">
                        <p style="margin:0 0 12px;font-size:14px;line-height:1.6;color:#667085;">Si el botón no funciona, copia este enlace en tu navegador:</p>
                        <p style="margin:0;font-size:13px;line-height:1.6;word-break:break-all;overflow-wrap:anywhere;"><a href="%s" style="color:#13795b;word-break:break-all;overflow-wrap:anywhere;">%s</a></p>
                      </td></tr>
                      <tr><td style="padding:18px 28px;border-top:1px solid #e5eaf1;text-align:center;">
                        <p style="margin:0 0 8px;font-size:13px;line-height:1.5;color:#667085;">La invitación vence el <strong>%s</strong>.</p>
                        <p style="margin:0;font-size:12px;line-height:1.5;color:#98a2b3;">Si no esperabas este correo, puedes ignorarlo.</p>
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
              </body>
            </html>
            """.formatted(introHtml,
                escape(invitation.verificationCode()), escape(link), escape(link), escape(link), escape(expiry));
        return new Content(subject, text, html);
    }

    /** Resaltado del cuerpo; el valor se escapa (el nombre de la clínica o de quien invita es dato de usuario). */
    private static String strong(String value) {
        return "<strong style=\"color:#172033;\">" + escape(value) + "</strong>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
