package com.odontorisas.presentation.dto;

import com.odontorisas.common.PatientStatus;
import com.odontorisas.service.patient.PatientView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * Ficha completa de un paciente. {@code minor} es <b>derivado</b> de {@code birthDate} (design.md
 * D2): el cliente no lo envía ni lo puede fijar. Se construye desde {@link PatientView} (capa de
 * servicio), <b>nunca</b> desde la entidad JPA (ataría el contrato al esquema de la BD).
 */
public record PatientResponse(
    Long id,
    String fullName,
    String document,
    LocalDate birthDate,
    @Schema(description = "Derivado de birthDate; el servidor es la autoridad.")
    boolean minor,
    String sex,
    String phone,
    String email,
    String address,
    String guardianName,
    String guardianRelationship,
    String guardianDocument,
    String guardianPhone,
    String emergencyContactName,
    String emergencyContactPhone,
    String emergencyContactRelationship,
    List<String> allergies,
    List<String> conditions,
    List<String> currentMedication,
    PatientStatus status) {

    public static PatientResponse from(PatientView v) {
        return new PatientResponse(
            v.id(), v.fullName(), v.document(), v.birthDate(), v.minor(), v.sex(),
            v.phone(), v.email(), v.address(),
            v.guardianName(), v.guardianRelationship(), v.guardianDocument(), v.guardianPhone(),
            v.emergencyContactName(), v.emergencyContactPhone(), v.emergencyContactRelationship(),
            v.allergies(), v.conditions(), v.currentMedication(), v.status());
    }
}
