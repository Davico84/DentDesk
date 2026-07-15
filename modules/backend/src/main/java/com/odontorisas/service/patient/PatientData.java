package com.odontorisas.service.patient;

import com.odontorisas.common.DocumentType;
import com.odontorisas.common.GuardianRelationship;
import com.odontorisas.common.Sex;

import java.time.LocalDate;
import java.util.List;

/**
 * Datos de una ficha de paciente en el lenguaje de la <b>capa de servicio</b> (entrada de alta
 * y edición). Existe para que {@code PatientService} no hable DTOs de {@code presentation}
 * (design.md D6): un servicio atado al transporte no se puede reutilizar y arrastra el contrato
 * HTTP hasta la lógica. La traducción desde/hacia los DTOs vive en el controller.
 *
 * <p>No incluye {@code id}, {@code status} ni timestamps: el estado se gobierna con las
 * operaciones de baja/reactivación, no editando este objeto. La <b>organización nunca viaja
 * aquí</b> — la pone el contexto de tenant.
 */
public record PatientData(
    String fullName,
    DocumentType documentType,
    String document,
    LocalDate birthDate,
    Sex sex,
    String phone,
    String email,
    String address,
    String guardianName,
    GuardianRelationship guardianRelationship,
    String guardianDocument,
    String guardianPhone,
    String emergencyContactName,
    String emergencyContactPhone,
    String emergencyContactRelationship,
    List<String> allergies,
    List<String> conditions,
    List<String> currentMedication) {
}
