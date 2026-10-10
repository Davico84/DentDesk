package com.odontorisas.service.patient;

import com.odontorisas.common.DocumentType;
import com.odontorisas.common.GuardianRelationship;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.common.Sex;
import com.odontorisas.persistence.entity.Patient;

import java.time.LocalDate;
import java.util.List;

/**
 * Ficha completa de un paciente, en el lenguaje de la capa de servicio (salida de consulta/edición).
 * Incluye {@code minor} <b>derivado</b> (design.md D2): se calcula al leer, nunca se persiste.
 * {@code phoneSharedWithOtherPatients} es un <b>aviso</b> (update-patients D1): indica que el
 * teléfono ya está en otros pacientes de la clínica; lo computa el servicio, no la entidad.
 * {@code anamnesisFrozen}: el paciente ya tiene Historia Clínica, así que su anamnesis no se edita
 * (`update-clinical-history` design.md D10).
 */
public record PatientView(
    Long id,
    String fullName,
    DocumentType documentType,
    String document,
    LocalDate birthDate,
    boolean minor,
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
    List<String> currentMedication,
    PatientStatus status,
    boolean phoneSharedWithOtherPatients,
    boolean anamnesisFrozen) {

    public static PatientView from(Patient p, boolean phoneSharedWithOtherPatients, boolean anamnesisFrozen) {
        return new PatientView(
            p.getId(), p.getFullName(), p.getDocumentType(), p.getDocument(), p.getBirthDate(), p.isMinor(), p.getSex(),
            p.getPhone(), p.getEmail(), p.getAddress(),
            p.getGuardianName(), p.getGuardianRelationship(), p.getGuardianDocument(), p.getGuardianPhone(),
            p.getEmergencyContactName(), p.getEmergencyContactPhone(), p.getEmergencyContactRelationship(),
            p.getAllergies(), p.getConditions(), p.getCurrentMedication(), p.getStatus(),
            phoneSharedWithOtherPatients, anamnesisFrozen);
    }
}
