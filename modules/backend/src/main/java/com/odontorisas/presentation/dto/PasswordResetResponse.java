package com.odontorisas.presentation.dto;

import com.odontorisas.service.auth.PasswordResetService.Outcome;
import com.odontorisas.service.auth.PasswordResetService.ResetResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Resultado del restablecimiento (design.md D1). Solo lo ve quien tiene un enlace vigente, es decir, quien
 * demostró ser dueño del correo: no contradice la anti-enumeración del login.
 */
public record PasswordResetResponse(
    @Schema(description = "`OK`: ya puede iniciar sesión. `LOCKED`: bloqueada por intentos fallidos hasta "
        + "`lockedUntil`. `ACCESS_DISABLED`: identidad o acceso a la clínica desactivados.",
        requiredMode = Schema.RequiredMode.REQUIRED)
    Outcome outcome,

    @Schema(description = "Hasta cuándo dura el bloqueo; solo con `LOCKED`.")
    Instant lockedUntil) {

    public static PasswordResetResponse from(ResetResult result) {
        return new PasswordResetResponse(result.outcome(), result.lockedUntil());
    }
}
