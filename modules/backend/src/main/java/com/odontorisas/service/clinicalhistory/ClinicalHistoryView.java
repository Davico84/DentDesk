package com.odontorisas.service.clinicalhistory;

import com.odontorisas.common.HistoriaClinicaStatus;
import com.odontorisas.persistence.entity.HistoriaClinica;

import java.time.Instant;
import java.util.Map;

/**
 * El documento inicial de la Historia Clínica, en el lenguaje de la capa de servicio. La
 * filiación (design.md D7) NO vive aquí: se deriva de {@code Patient} en el mapper de
 * presentación, para no atar este servicio a los datos del paciente.
 */
public record ClinicalHistoryView(
    Long id,
    Long patientId,
    HistoriaClinicaStatus status,
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
    Instant completedAt) {

    public static ClinicalHistoryView from(HistoriaClinica h) {
        return new ClinicalHistoryView(
            h.getId(), h.getPatientId(), h.getStatus(),
            h.getMotivoConsulta(), h.getEnfermedadActual(), h.getAntecedentesPersonales(),
            h.getExamenClinico(), h.getPronostico(), h.getSignosVitales(),
            h.isAlergiasRevisado(), h.getAlergiasDetalle(),
            h.isMedicacionRevisado(), h.getMedicacionDetalle(),
            h.isAntecedentesFamiliaresRevisado(), h.getAntecedentesFamiliaresDetalle(),
            h.getCompletedAt());
    }
}
