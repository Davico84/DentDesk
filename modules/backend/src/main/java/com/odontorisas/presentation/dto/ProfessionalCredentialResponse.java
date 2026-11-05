package com.odontorisas.presentation.dto;

import com.odontorisas.service.staff.ProfessionalCredentialService;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/** La credencial propia y la capacidad clínica en la clínica activa (design.md D6). */
public record ProfessionalCredentialResponse(
    @Schema(description = "La credencial declarada, o null si nunca la declaró.", nullable = true)
    Credential credential,
    @Schema(description = "Capacidad en la clínica activa; null sin clínica activa.", nullable = true)
    ClinicalCapacityResponse clinicalCapacity) {

    public record Credential(String copNumber, LocalDate licensedUntil, Instant swornAt) {}

    public static ProfessionalCredentialResponse from(ProfessionalCredentialService.MyCredentialView view) {
        return new ProfessionalCredentialResponse(
            view.credential().map(c -> new Credential(c.copNumber(), c.licensedUntil(), c.swornAt())).orElse(null),
            view.clinicalCapacity().map(ClinicalCapacityResponse::from).orElse(null));
    }
}
