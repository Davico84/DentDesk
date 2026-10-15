package com.odontorisas.infra.mail;

import com.odontorisas.service.staff.InvitationDeliveryException;
import com.odontorisas.service.staff.InvitationMailer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfiguration {
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
}
