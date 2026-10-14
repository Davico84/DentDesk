package com.odontorisas.service.staff;

import com.odontorisas.common.Role;

import java.time.Instant;

/** Puerto de aplicación para entregar invitaciones por correo. */
public interface InvitationMailer {
    void send(InvitationMail invitation);

    record InvitationMail(String recipient, String organizationName, Role role,
                          String rawToken, String verificationCode, Instant expiresAt) {}
}
