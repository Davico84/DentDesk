package com.odontorisas.presentation.dto;

import com.odontorisas.service.platform.PlatformOrganizationService;

import java.time.Instant;

public record PlatformOrganizationResponse(Long organizationId, String name, String slug,
                                           String adminEmail,
                                           Instant invitationExpiresAt) {
    public static PlatformOrganizationResponse from(PlatformOrganizationService.ProvisionedOrganization result) {
        return new PlatformOrganizationResponse(result.organizationId(), result.name(), result.slug(),
            result.invitation().email(), result.invitation().expiresAt());
    }
}
