package com.odontorisas.presentation.dto;

import com.odontorisas.common.Role;
import com.odontorisas.service.staff.InvitationState;
import com.odontorisas.service.staff.StaffService;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Invitación en el listado del administrador (add-staff). Sin token ni código: viajan solo en el correo.
 * {@code state} incluye {@code EXPIRED}, derivado de una pendiente con la vigencia terminada.
 */
public record StaffInvitationResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Role role,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) InvitationState state,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
        description = "Si la clínica puede reenviarla o revocarla (MEMBER pendiente o vencida).")
    boolean manageable) {

    public static StaffInvitationResponse from(StaffService.InvitationListView view) {
        return new StaffInvitationResponse(view.id(), view.email(), view.role(), view.state(), view.createdAt(),
            view.expiresAt(), view.manageable());
    }
}
