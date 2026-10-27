package com.odontorisas.presentation.dto;

import com.odontorisas.common.ConsultationNoteStatus;
import com.odontorisas.service.consultationnote.ConsultationNoteView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** La nota con lo que cuelga de ella; el texto original nunca se reescribe: las correcciones van aparte. */
public record ConsultationNoteResponse(
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
    @Schema(description = "Quien selló (firmante); el único que puede corregir la nota.")
    Long sealedBy,
    @Schema(description = "Credencial del firmante, copiada al sellar; null si está ABIERTA o se firmó antes del "
        + "registro de credenciales.", nullable = true)
    SignerResponse signer,
    @Schema(description = "true si está SELLADA pero se firmó antes del registro de credenciales (sin firmante).")
    boolean signedBeforeCredentials,
    @Schema(description = "Cuándo se escribió la nota (distinto de attendedAt).")
    Instant createdAt,
    Long createdBy,
    Instant updatedAt,
    List<NoteDiagnosisResponse> diagnoses,
    List<BackgroundEntryResponse> backgroundEntries,
    List<CorrectionResponse> corrections,
    @Schema(description = "Hallazgos del odontograma atendidos en esta consulta.")
    List<NoteFindingResponse> attendedFindings) {

    public static ConsultationNoteResponse from(ConsultationNoteView v) {
        return new ConsultationNoteResponse(v.id(), v.patientId(), v.status(), v.attendedAt(), v.reason(),
            v.illnessDuration(), v.symptomsAndSigns(), v.previousTreatment(), v.biologicalFunctions(),
            v.examination(), v.treatment(), v.auxiliaryExams(), v.referral(), v.nextAppointmentOn(), v.sealedAt(),
            v.sealedBy(), SignerResponse.from(v.signer()), v.signedBeforeCredentials(), v.createdAt(),
            v.createdBy(), v.updatedAt(),
            v.diagnoses().stream().map(NoteDiagnosisResponse::from).toList(),
            v.backgroundEntries().stream().map(BackgroundEntryResponse::from).toList(),
            v.corrections().stream().map(CorrectionResponse::from).toList(),
            v.attendedFindings().stream().map(NoteFindingResponse::from).toList());
    }
}
