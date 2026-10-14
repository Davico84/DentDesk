package com.odontorisas.presentation.dto;

import com.odontorisas.common.Role;
import com.odontorisas.service.staff.StaffService;

import java.time.Instant;

public record InvitationResponse(Long id, String email, Role role, Instant expiresAt) {
    public static InvitationResponse from(StaffService.IssuedInvitation invitation) {
        return new InvitationResponse(invitation.id(), invitation.email(), invitation.role(), invitation.expiresAt());
    }
}
