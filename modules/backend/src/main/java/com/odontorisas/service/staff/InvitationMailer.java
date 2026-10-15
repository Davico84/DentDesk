package com.odontorisas.service.staff;

import com.odontorisas.common.Role;

import java.time.Instant;

/** Puerto de aplicación para entregar invitaciones por correo. */
public interface InvitationMailer {
    void send(InvitationMail invitation);

    /** {@code inviterName}: quien emitió la invitación; el correo lo nombra solo en las de clínica. */
    record InvitationMail(String recipient, String organizationName, Role role, String inviterName,
                          String rawToken, String verificationCode, Instant expiresAt) {}
}
