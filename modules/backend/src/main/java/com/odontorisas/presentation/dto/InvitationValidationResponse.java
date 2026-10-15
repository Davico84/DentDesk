package com.odontorisas.presentation.dto;

import com.odontorisas.common.Role;
import com.odontorisas.service.staff.StaffService;

import java.time.Instant;

public record InvitationValidationResponse(String email, Role role, String organizationName, Instant expiresAt) {
    public static InvitationValidationResponse from(StaffService.InvitationView invitation) {
        return new InvitationValidationResponse(invitation.email(), invitation.role(), invitation.organizationName(), invitation.expiresAt());
    }
}
