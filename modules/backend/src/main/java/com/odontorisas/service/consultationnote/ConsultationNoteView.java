package com.odontorisas.service.consultationnote;

import com.odontorisas.common.ConsultationNoteStatus;
import com.odontorisas.common.SignerCredential;
import com.odontorisas.persistence.entity.ConsultationNote;

import com.odontorisas.persistence.entity.Signature;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Una nota con todo lo que cuelga de ella (spec: <i>Leer una nota con su contenido completo</i>):
 * el texto original y, aparte, sus correcciones — el original nunca se reescribe (design.md D6).
 */
public record ConsultationNoteView(
    Long id,
    Long patientId,
    ConsultationNoteStatus status,
    Instant attendedAt,
    String reason,
    String illnessDuration,
    String symptomsAndSigns,
    String previousTreatment,
    String biologicalFunctions,
    String examination,
    String treatment,
    String auxiliaryExams,
    String referral,
    LocalDate nextAppointmentOn,
    Instant sealedAt,
    Long sealedBy,
    SignerCredential signer,
    boolean signedBeforeCredentials,
    Instant createdAt,
    Long createdBy,
    Instant updatedAt,
    List<NoteDiagnosisView> diagnoses,
    List<BackgroundEntryView> backgroundEntries,
    List<CorrectionView> corrections,
    List<NoteFindingView> attendedFindings) {

    public static ConsultationNoteView from(ConsultationNote n, List<NoteDiagnosisView> diagnoses,
                                            List<BackgroundEntryView> entries, List<CorrectionView> corrections,
                                            List<NoteFindingView> attendedFindings) {
        return new ConsultationNoteView(n.getId(), n.getPatientId(), n.getStatus(), n.getAttendedAt(),
            n.getReason(), n.getIllnessDuration(), n.getSymptomsAndSigns(), n.getPreviousTreatment(),
            n.getBiologicalFunctions(), n.getExamination(), n.getTreatment(), n.getAuxiliaryExams(),
            n.getReferral(), n.getNextAppointmentOn(), n.getSealedAt(), n.getSealedBy(), Signature.read(n.getSignature()),
            // Sellada sin foto: anterior al registro de credenciales (update-staff-professional-credential D5).
            n.getStatus() == ConsultationNoteStatus.SELLADA && n.getSignature() == null, n.getCreatedAt(),
            n.getCreatedBy(), n.getUpdatedAt(), diagnoses, entries, corrections, attendedFindings);
    }
}
