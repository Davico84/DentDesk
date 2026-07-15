package com.odontorisas.presentation.dto;

import com.odontorisas.presentation.dto.validation.GuardianRequiredForMinor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Alta y edición de un paciente (el mismo cuerpo sirve para ambas). Solo {@code fullName} y
 * {@code birthDate} son obligatorios (design.md OQ1); el documento es opcional (design.md D1).
 * Si la fecha indica menor de edad, {@link GuardianRequiredForMinor} exige los datos del
 * responsable. La organización <b>no viaja aquí</b>: la pone el contexto de tenant.
 */
@GuardianRequiredForMinor
public record PatientRequest(
    @NotBlank @Size(max = 200)
    @Schema(example = "Ada Lovelace")
    String fullName,

    @Size(max = 50)
    @Schema(example = "12345678", description = "Documento de identidad. Opcional; único por clínica.")
    String document,

    @NotNull @PastOrPresent
    @Schema(example = "2015-05-10", description = "De ella se deriva la minoría de edad.")
    LocalDate birthDate,

    @Size(max = 20)
    @Schema(example = "F")
    String sex,

    @Size(max = 40)
    @Schema(example = "+51987654321")
    String phone,

    @Email @Size(max = 255)
    @Schema(example = "ada@example.com")
    String email,

    @Size(max = 500)
    String address,

    @Size(max = 200)
    @Schema(description = "Nombre del responsable. Obligatorio si el paciente es menor.")
    String guardianName,

    @Size(max = 50)
    @Schema(example = "madre", description = "Parentesco del responsable. Obligatorio si es menor.")
    String guardianRelationship,

    @Size(max = 50)
    @Schema(description = "Documento del responsable. Opcional, incluso para menores.")
    String guardianDocument,

    @Size(max = 40)
    @Schema(description = "Teléfono del responsable. Obligatorio si el paciente es menor.")
    String guardianPhone,

    @Size(max = 200)
    String emergencyContactName,

    @Size(max = 40)
    String emergencyContactPhone,

    @Size(max = 50)
    String emergencyContactRelationship,

    @Schema(description = "Alergias, como lista de valores (no texto libre).")
    List<String> allergies,

    @Schema(description = "Condiciones/antecedentes, como lista de valores.")
    List<String> conditions,

    @Schema(description = "Medicación actual, como lista de valores.")
    List<String> currentMedication) {
}
