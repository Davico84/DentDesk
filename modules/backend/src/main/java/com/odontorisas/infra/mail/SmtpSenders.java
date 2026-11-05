package com.odontorisas.infra.mail;

import org.springframework.mail.javamail.JavaMailSenderImpl;

/** El {@link JavaMailSenderImpl} común a los adaptadores SMTP: STARTTLS obligatorio y timeouts acotados. */
final class SmtpSenders {

    private SmtpSenders() {
    }

    static JavaMailSenderImpl create(MailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
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
        return sender;
    }
}
