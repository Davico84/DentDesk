package com.odontorisas.service.admission;

import com.odontorisas.service.clinicalhistory.ClinicalHistoryView;
import com.odontorisas.service.dentalchart.DentalChartBlockView;

/** El resultado de cerrar la admisión: la Historia Clínica y el odontograma, ambos sellados en la
 *  misma transacción (ADR-0012 D5, design.md D4). */
public record AdmissionClosedResult(ClinicalHistoryView clinicalHistory, DentalChartBlockView dentalChart) {
}
