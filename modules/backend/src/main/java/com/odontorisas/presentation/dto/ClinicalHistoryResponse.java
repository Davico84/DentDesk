package com.odontorisas.presentation.dto;

import com.odontorisas.common.HistoriaClinicaStatus;
import com.odontorisas.common.Sex;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryView;
import com.odontorisas.service.patient.PatientView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * El documento de la Historia Clínica, con su filiación y sus diagnósticos.
 *
 * <p><b>La filiación se compone aquí, leyéndola de {@code Patient}</b> (design.md D7): no se
 * duplica ni se guarda en {@code historia_clinica}. Por eso siempre refleja el dato vigente del
 * paciente, incluso en una Historia {@code COMPLETED} — la filiación NO forma parte de lo sellado.
 */
public record ClinicalHistoryResponse(
    Long id,
    Long patientId,
    HistoriaClinicaStatus status,

    // --- Filiación, leída de Patient (D7) ---
    String fullName,
    Sex sex,
    LocalDate birthDate,
    String address,
    String phone,

    // --- Documento inicial ---
    String motivoConsulta,
    String enfermedadActual,
    String antecedentesPersonales,
    String examenClinico,
    String pronostico,
    Map<String, Object> signosVitales,
    boolean alergiasRevisado,
    String alergiasDetalle,
    boolean medicacionRevisado,
    String medicacionDetalle,
    boolean antecedentesFamiliaresRevisado,
    String antecedentesFamiliaresDetalle,

    List<DiagnosisResponse> diagnosticos,

    @Schema(description = "Momento del sellado a COMPLETED; null mientras esté en DRAFT.")
    Instant completedAt) {

    public static ClinicalHistoryResponse from(ClinicalHistoryView h, PatientView patient,
                                                List<DiagnosisResponse> diagnosticos) {
        return new ClinicalHistoryResponse(
            h.id(), h.patientId(), h.status(),
            patient.fullName(), patient.sex(), patient.birthDate(), patient.address(), patient.phone(),
            h.motivoConsulta(), h.enfermedadActual(), h.antecedentesPersonales(), h.examenClinico(),
            h.pronostico(), h.signosVitales(),
            h.alergiasRevisado(), h.alergiasDetalle(),
            h.medicacionRevisado(), h.medicacionDetalle(),
            h.antecedentesFamiliaresRevisado(), h.antecedentesFamiliaresDetalle(),
            diagnosticos, h.completedAt());
    }
}
