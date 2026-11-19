package com.odontorisas.infra.mail;

import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * El {@link JavaMailSenderImpl} común a los adaptadores SMTP, con timeouts acotados. Por defecto, STARTTLS
 * obligatorio y autenticación: si el servidor no ofrece STARTTLS el envío falla antes de transmitir el mensaje.
 * En modo local ({@link MailProperties#localSmtp()}, solo con host de loopback) habla con un servidor de prueba
 * sin STARTTLS ni autenticación ({@code update-project-foundation-local-mail} design D1).
 */
final class SmtpSenders {

    private SmtpSenders() {
    }

    static JavaMailSenderImpl create(MailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.host() == null ? null : properties.host().strip());
        sender.setPort(properties.port());
        if (properties.localSmtp()) {
            // Sin credenciales: el servidor de prueba no autentica y no hay nada que proteger.
            sender.getJavaMailProperties().put("mail.smtp.auth", "false");
            sender.getJavaMailProperties().put("mail.smtp.starttls.enable", "false");
            sender.getJavaMailProperties().put("mail.smtp.starttls.required", "false");
        } else {
            sender.setUsername(properties.username());
            sender.setPassword(properties.password());
            sender.getJavaMailProperties().put("mail.smtp.auth", "true");
            sender.getJavaMailProperties().put("mail.smtp.starttls.enable", "true");
            sender.getJavaMailProperties().put("mail.smtp.starttls.required", "true");
        }
        String timeoutMillis = String.valueOf(properties.timeout().toMillis());
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", timeoutMillis);
        sender.getJavaMailProperties().put("mail.smtp.timeout", timeoutMillis);
        sender.getJavaMailProperties().put("mail.smtp.writetimeout", timeoutMillis);
        return sender;
    }
}
