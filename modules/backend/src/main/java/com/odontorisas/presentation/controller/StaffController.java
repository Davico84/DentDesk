package com.odontorisas.presentation.controller;

import com.odontorisas.presentation.dto.InvitationRequest;
import com.odontorisas.presentation.dto.InvitationResponse;
import com.odontorisas.presentation.dto.MemberResponse;
import com.odontorisas.presentation.dto.MemberStatusRequest;
import com.odontorisas.service.staff.StaffService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/staff")
@PreAuthorize("hasRole('ADMIN')")
public class StaffController {
    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @PostMapping("/invitations")
    public InvitationResponse invite(@Valid @RequestBody InvitationRequest request) {
        return InvitationResponse.from(staffService.invite(request.email(), request.role()));
    }

    @GetMapping("/members")
    public List<MemberResponse> members() {
        return staffService.members().stream().map(MemberResponse::from).toList();
    }

    @DeleteMapping("/invitations/{id}")
    public void revoke(@PathVariable Long id) {
        staffService.revoke(id);
    }

    @PatchMapping("/members/{id}/status")
    public void changeStatus(@PathVariable Long id, @Valid @RequestBody MemberStatusRequest request) {
        staffService.changeStatus(id, request.status());
    }
}
