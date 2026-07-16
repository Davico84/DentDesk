package com.odontorisas.presentation.dto;

import com.odontorisas.common.DocumentType;
import com.odontorisas.common.GuardianRelationship;
import com.odontorisas.common.Sex;
import com.odontorisas.presentation.dto.validation.GuardianRequiredForMinor;
import com.odontorisas.presentation.dto.validation.ValidDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Alta y edición de un paciente (el mismo cuerpo sirve para ambas). Solo {@code fullName} y
 * {@code birthDate} son obligatorios (design.md OQ1). El documento es opcional pero va
 * <b>emparejado con su tipo</b> y con <b>formato según el tipo</b> ({@link ValidDocument}); si el
 * paciente es menor, {@link GuardianRequiredForMinor} exige los datos del responsable. Los
 * teléfonos son texto con formato numérico (dígitos, {@code +} y espacios), no un tipo numérico.
 * La organización <b>no viaja aquí</b>: la pone el contexto de tenant.
 */
@GuardianRequiredForMinor
@ValidDocument
public record PatientRequest(
    @NotBlank @Size(max = 200)
    @Schema(example = "Ada Lovelace")
    String fullName,

    @Schema(description = "Tipo de documento. Obligatorio si se envía el número (y viceversa).")
    DocumentType documentType,

    @Size(max = 50)
    @Schema(example = "12345678", description = "Número de documento. Formato según el tipo. Único por clínica.")
    String document,

    @NotNull @PastOrPresent
    @Schema(example = "2015-05-10", description = "De ella se deriva la minoría de edad.")
    LocalDate birthDate,

    @Schema(example = "FEMALE", description = "Sexo (conjunto cerrado).")
    Sex sex,

    @Pattern(regexp = PHONE, message = "El teléfono solo admite dígitos, «+» y espacios")
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

    @Schema(description = "Parentesco del responsable. Obligatorio si es menor.")
    GuardianRelationship guardianRelationship,

    @Size(max = 50)
    @Schema(description = "Documento del responsable. Opcional, incluso para menores.")
    String guardianDocument,

    @Pattern(regexp = PHONE, message = "El teléfono solo admite dígitos, «+» y espacios")
    @Schema(description = "Teléfono del responsable. Obligatorio si el paciente es menor.")
    String guardianPhone,

    @Size(max = 200)
    String emergencyContactName,

    @Pattern(regexp = PHONE, message = "El teléfono solo admite dígitos, «+» y espacios")
    String emergencyContactPhone,

    @Size(max = 50)
    String emergencyContactRelationship,

    @Schema(description = "Alergias, como lista de valores (no texto libre).")
    List<String> allergies,

    @Schema(description = "Condiciones/antecedentes, como lista de valores.")
    List<String> conditions,

    @Schema(description = "Medicación actual, como lista de valores.")
    List<String> currentMedication) {

    /** Teléfono: texto con formato numérico (dígitos, «+» y espacios), 7–20. NUNCA un tipo numérico. */
    static final String PHONE = "^\\+?[0-9 ]{7,20}$";
}
