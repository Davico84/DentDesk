package com.odontorisas.infra.mail;

import com.odontorisas.service.auth.AccountMailer;
import com.odontorisas.service.staff.InvitationDeliveryException;
import com.odontorisas.service.staff.InvitationMailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfiguration {
    private static final Logger log = LoggerFactory.getLogger(MailConfiguration.class);

    @Bean
    @ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
    InvitationMailer smtpInvitationMailer(MailProperties properties) {
        return new SmtpInvitationMailer(properties);
    }

    /**
     * Sin correo configurado la invitación se rechaza en vez de descartarse en silencio: el token
     * solo viaja por correo (la API no lo devuelve), así que una invitación no entregada quedaría
     * pendiente e inutilizable — y la inicial dejaría una clínica sin forma de entrar. El 503 revierte
     * la transacción que la emite (add-platform-admin D6). Los tests sustituyen este bean.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
    InvitationMailer disabledInvitationMailer() {
        return invitation -> {
            throw new InvitationDeliveryException(
                "El envío de correo no está configurado en este entorno; no se puede emitir la invitación.");
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
    AccountMailer smtpAccountMailer(MailProperties properties) {
        return new SmtpAccountMailer(properties);
    }

    /**
     * A diferencia de la invitación, sin correo configurado estos correos se registran y descartan
     * ({@code update-authentication-password-recovery} design.md D4): se envían fuera de la petición, que ya
     * respondió. En ese entorno la recuperación no funciona, pero la respuesta sigue siendo la misma. El log no
     * lleva el destinatario ni el token.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
    AccountMailer disabledAccountMailer() {
        return new AccountMailer() {
            @Override
            public void sendRecoveryLink(RecoveryMail mail) {
                log.warn("Correo deshabilitado: se descarta un enlace de recuperación.");
            }

            @Override
            public void sendPasswordChanged(PasswordChangedMail mail) {
                log.warn("Correo deshabilitado: se descarta un aviso de cambio de contraseña.");
            }
        };
    }
}
