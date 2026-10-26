package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/**
 * Declaración o renovación de la credencial propia. Paridad con el schema Zod del frontend
 * ({@code docs/coding-style.md} §7). "No anterior a hoy" la decide el servicio en la hora de Perú.
 */
public record ProfessionalCredentialRequest(
    @Schema(description = "Número de colegiatura COP: solo dígitos, de 1 a 10.", example = "12345")
    @NotBlank @Pattern(regexp = "\\d{1,10}", message = "El número de colegiatura solo admite dígitos (hasta 10).")
    String copNumber,

    @Schema(description = "Último día de habilitación, en la hora de Perú. Hoy o posterior.")
    @NotNull
    LocalDate licensedUntil,

    @Schema(description = "Aceptación de la declaración jurada; debe ser true.")
    @NotNull @AssertTrue(message = "Debes aceptar la declaración jurada.")
    Boolean sworn) {}
