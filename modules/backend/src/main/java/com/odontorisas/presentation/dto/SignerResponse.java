package com.odontorisas.presentation.dto;

import com.odontorisas.common.SignerCredential;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** Credencial de quien firmó, tal como era al firmar (design.md D5/D6). */
public record SignerResponse(
    String fullName,
    @Schema(description = "Número de colegiatura COP del firmante.")
    String copNumber,
    @Schema(description = "Fecha \"habilitado hasta\" vigente al firmar.")
    LocalDate licensedUntil) {

    public static SignerResponse from(SignerCredential signer) {
        return signer == null ? null : new SignerResponse(signer.fullName(), signer.copNumber(), signer.licensedUntil());
    }
}
