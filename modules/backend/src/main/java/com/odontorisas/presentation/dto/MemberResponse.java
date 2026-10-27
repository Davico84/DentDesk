package com.odontorisas.presentation.dto;

import com.odontorisas.common.MembershipStatus;
import com.odontorisas.common.Role;
import com.odontorisas.service.staff.StaffService;
import io.swagger.v3.oas.annotations.media.Schema;

public record MemberResponse(Long id, String email, String fullName, Role role, MembershipStatus status,
                             @Schema(description = "Capacidad clínica del miembro en esta clínica.")
                             ClinicalCapacityResponse clinicalCapacity) {
    public static MemberResponse from(StaffService.MemberView member) {
        return new MemberResponse(member.id(), member.email(), member.fullName(), member.role(), member.status(),
            ClinicalCapacityResponse.from(member.clinicalCapacity()));
    }
}
