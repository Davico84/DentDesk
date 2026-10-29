package com.odontorisas.infra.mail;

import com.odontorisas.service.auth.AccountMailDeliveryException;
import com.odontorisas.service.auth.AccountMailer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Adaptador SMTP de los correos de la cuenta ({@code update-authentication-password-recovery} design.md D4,
 * D6), sobre el mismo {@link MailProperties} que las invitaciones. Los firma Dentdesk.
 *
 * <p><b>Seguimiento de clics de Brevo:</b> no hay un header SMTP documentado que lo apague por mensaje, así que
 * no se fija ninguno. Que el enlace llegue sin reescribir se verifica en la cuenta, antes de desplegar (tasks
 * 4.3, bloqueante).
 */
final class SmtpAccountMailer implements AccountMailer {

    /** Hora de Perú: es la del titular de la cuenta. */
    private static final DateTimeFormatter PERU_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm")
        .withZone(ZoneId.of("America/Lima"));

    private final MailProperties properties;
    private final JavaMailSenderImpl sender;

    SmtpAccountMailer(MailProperties properties) {
        this.properties = properties;
        this.sender = SmtpSenders.create(properties);
    }

    @Override
    public void sendRecoveryLink(RecoveryMail mail) {
        send(mail.recipient(), composeRecovery(mail, properties.frontendUrl()),
            "No se pudo enviar el enlace de recuperación por correo.");
    }

    @Override
    public void sendPasswordChanged(PasswordChangedMail mail) {
        send(mail.recipient(), composePasswordChanged(mail),
            "No se pudo enviar el aviso de cambio de contraseña por correo.");
    }

    private void send(String recipient, Content content, String failure) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.from());
            helper.setTo(recipient);
            helper.setSubject(content.subject());
            helper.setText(content.text(), content.html());
            sender.send(message);
        } catch (MessagingException | MailException ex) {
            // El mensaje de la excepción de JavaMail no lleva el cuerpo: ni el token ni el enlace.
            throw new AccountMailDeliveryException(failure, ex);
        }
    }

    record Content(String subject, String text, String html) {}

    /** Arma el correo del enlace sin enviarlo. El enlace apunta a la pantalla del frontend, nunca a la API. */
    static Content composeRecovery(RecoveryMail mail, String frontendUrl) {
        String link = frontendUrl.replaceAll("/$", "")
            + "/auth/reset-password?token="
            + URLEncoder.encode(mail.rawToken(), StandardCharsets.UTF_8);
        String subject = "Dentdesk: recupera tu contraseña";
        String text = "Recibimos un pedido para recuperar la contraseña de tu cuenta de Dentdesk.\n\n"
            + "Elige una contraseña nueva aquí:\n" + link + "\n\n"
            + "El enlace vale 30 minutos y se usa una sola vez.\n"
            + "Si no lo pediste, ignora este correo: tu contraseña no cambia.";
        String body = """
            <p style="margin:0 0 20px;font-size:16px;line-height:1.6;color:#4b5565;">Recibimos un pedido para recuperar la contraseña de tu cuenta de Dentdesk.</p>
            <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:0 auto 20px;">
              <tr><td align="center" bgcolor="#13795b" style="border-radius:8px;">
                <a href="%s" style="display:inline-block;padding:14px 26px;border:1px solid #13795b;border-radius:8px;color:#ffffff;font-size:16px;font-weight:bold;text-decoration:none;">Elegir contraseña nueva</a>
              </td></tr>
            </table>
            <p style="margin:0 0 12px;font-size:14px;line-height:1.6;color:#667085;">Si el botón no funciona, copia este enlace en tu navegador:</p>
            <p style="margin:0 0 20px;font-size:13px;line-height:1.6;word-break:break-all;overflow-wrap:anywhere;"><a href="%s" style="color:#13795b;">%s</a></p>
            <p style="margin:0 0 8px;font-size:13px;line-height:1.5;color:#667085;">El enlace vale <strong>30 minutos</strong> y se usa una sola vez.</p>
            <p style="margin:0;font-size:12px;line-height:1.5;color:#98a2b3;">Si no lo pediste, ignora este correo: tu contraseña no cambia.</p>
            """.formatted(escape(link), escape(link), escape(link));
        return new Content(subject, text, layout("Recupera tu contraseña", body));
    }

    /**
     * Arma el aviso sin enviarlo. Sin enlaces que cambien la contraseña (D6): no se entrena a abrir enlaces de
     * correos de "tu cuenta cambió". Qué hacer se dice en texto.
     */
    static Content composePasswordChanged(PasswordChangedMail mail) {
        String when = PERU_TIME.format(mail.changedAt());
        String subject = "Dentdesk: tu contraseña cambió";
        String text = "La contraseña de tu cuenta de Dentdesk se cambió el " + when + " (hora de Perú).\n\n"
            + "Si fuiste tú, no tienes que hacer nada.\n"
            + "Si no fuiste tú, pide la recuperación de la contraseña desde la pantalla de inicio de sesión "
            + "de Dentdesk y avisa al administrador de tu clínica.";
        String body = """
            <p style="margin:0 0 16px;font-size:16px;line-height:1.6;color:#4b5565;">La contraseña de tu cuenta de Dentdesk se cambió el <strong style="color:#172033;">%s</strong> (hora de Perú).</p>
            <p style="margin:0 0 8px;font-size:14px;line-height:1.6;color:#667085;">Si fuiste tú, no tienes que hacer nada.</p>
            <p style="margin:0;font-size:14px;line-height:1.6;color:#667085;">Si no fuiste tú, pide la recuperación de la contraseña desde la pantalla de inicio de sesión de Dentdesk y avisa al administrador de tu clínica.</p>
            """.formatted(escape(when));
        return new Content(subject, text, layout("Tu contraseña cambió", body));
    }

    private static String layout(String title, String body) {
        return """
            <!doctype html>
            <html lang="es">
              <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
              </head>
              <body style="margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%" style="background-color:#f4f7fb;width:100%%;">
                  <tr><td align="center" style="padding:32px 12px;">
                    <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="600" style="width:100%%;max-width:600px;background-color:#ffffff;border:1px solid #e5eaf1;border-radius:16px;">
                      <tr><td style="padding:32px 28px 12px;text-align:center;">
                        <div style="display:inline-block;padding:10px 16px;border-radius:999px;background-color:#e8f5f1;color:#13795b;font-size:13px;font-weight:bold;letter-spacing:1px;">DENTDESK</div>
                      </td></tr>
                      <tr><td style="padding:12px 28px 32px;text-align:center;">
                        <h1 style="margin:0 0 14px;font-size:24px;line-height:1.25;color:#172033;">%s</h1>
                        %s
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
              </body>
            </html>
            """.formatted(escape(title), escape(title), body);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
