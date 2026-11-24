package com.odontorisas.presentation.dto;

import com.odontorisas.service.staff.ClinicalCapacityService.EnabledMember;
import io.swagger.v3.oas.annotations.media.Schema;

/** Odontólogo agendable: miembro con capacidad clínica vigente hoy. Solo id y nombre. */
public record SchedulableDentistResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "7") Long userId,
    @Schema(example = "Dra. Ríos") String fullName) {

    public static SchedulableDentistResponse from(EnabledMember member) {
        return new SchedulableDentistResponse(member.userId(), member.fullName());
    }
}
