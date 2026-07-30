package com.odontorisas.service.clinicalhistory;

import com.odontorisas.persistence.entity.HistoriaClinica;

import java.util.ArrayList;
import java.util.List;

/**
 * El checklist mínimo que la transición a {@code COMPLETED} exige (design.md D4). Puro y estático
 * a propósito: no depende de Spring ni de la base de datos, se puede testear como una función.
 *
 * <p>Los nombres devueltos son los campos del request (paridad con el frontend, que los usa para
 * marcar qué falta) — no texto libre.
 */
final class ClinicalHistoryChecklist {

    private ClinicalHistoryChecklist() {
    }

    /** Campos que faltan para poder sellar; vacío si el checklist está satisfecho. */
    static List<String> missingFields(HistoriaClinica h, long diagnosisCount) {
        List<String> missing = new ArrayList<>();
        if (isBlank(h.getMotivoConsulta())) {
            missing.add("motivoConsulta");
        }
        if (isBlank(h.getAntecedentesPersonales())) {
            missing.add("antecedentesPersonales");
        }
        if (!h.isAlergiasRevisado()) {
            missing.add("alergiasRevisado");
        }
        if (!h.isMedicacionRevisado()) {
            missing.add("medicacionRevisado");
        }
        if (!h.isAntecedentesFamiliaresRevisado()) {
            missing.add("antecedentesFamiliaresRevisado");
        }
        if (isBlank(h.getExamenClinico())) {
            missing.add("examenClinico");
        }
        if (diagnosisCount < 1) {
            missing.add("diagnosticos");
        }
        if (isBlank(h.getPronostico())) {
            missing.add("pronostico");
        }
        return missing;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
