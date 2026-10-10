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

    /**
     * Campos que faltan para poder sellar; vacío si el checklist está satisfecho.
     *
     * @param hasInitialDentalChart {@code true} si el paciente tiene un odontograma inicial
     *                              (cualquier estado). El sellado de <b>ambos</b> documentos es
     *                              atómico (ADR-0012 D5, `add-dental-chart`): la Historia ya no se
     *                              sella sola. La completitud propia del odontograma
     *                              (especificaciones y observaciones revisadas) la exige su
     *                              propio checklist en {@code DentalChartChecklist} — este ítem
     *                              solo comprueba que exista, sin conocer el tipo de dental-chart
     *                              (design.md D4: ningún servicio conoce al otro).
     */
    static List<String> missingFields(HistoriaClinica h, long diagnosisCount, boolean hasInitialDentalChart) {
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
        if (!hasInitialDentalChart) {
            missing.add("dentalChart");
        }
        return missing;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
