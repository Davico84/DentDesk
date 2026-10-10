package com.odontorisas.service.clinicalhistory;

import com.odontorisas.common.DiagnosisLocation;
import com.odontorisas.common.TipoDiagnostico;

/**
 * Un diagnóstico en el lenguaje de la <b>capa de servicio</b> — mismo patrón que
 * {@code ClinicalHistoryData}. La pareja ubicación/FDI ya llega validada por
 * {@code @ValidDiagnosisLocation} en el DTO; el servicio solo valida lo que necesita la base de
 * datos (que el código CIE10 exista).
 */
public record DiagnosisData(
    String cie10Code,
    DiagnosisLocation ubicacion,
    Integer fdiNumber,
    TipoDiagnostico tipoDiagnostico) {
}
