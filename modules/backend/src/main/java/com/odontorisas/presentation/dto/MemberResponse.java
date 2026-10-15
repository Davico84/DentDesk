package com.odontorisas.presentation.dto;

import com.odontorisas.common.MembershipStatus;
import com.odontorisas.common.Role;
import com.odontorisas.service.staff.StaffService;

public record MemberResponse(Long id, String email, String fullName, Role role, MembershipStatus status) {
    public static MemberResponse from(StaffService.MemberView member) {
        return new MemberResponse(member.id(), member.email(), member.fullName(), member.role(), member.status());
    }
}
