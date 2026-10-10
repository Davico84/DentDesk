package com.odontorisas.service.clinicalhistory;

import java.util.Map;

/**
 * Datos del documento inicial de la Historia Clínica en el lenguaje de la <b>capa de servicio</b>
 * (entrada de creación y guardado en `DRAFT`) — mismo patrón que {@code PatientData}
 * (design.md D6 de `patients`). No incluye {@code id}, {@code patientId}, {@code status} ni
 * timestamps: el ciclo de vida se gobierna con operaciones propias (crear, sellar), no editando
 * este objeto.
 *
 * <p>Los pares {@code *Revisado`/`*Detalle} son el patrón "campo vacío ≠ revisado" (design.md D3):
 * {@code revisado=true, detalle=null} ⇒ "sin alergias conocidas"; {@code revisado=false} ⇒ no
 * revisado, sin importar el detalle.
 */
public record ClinicalHistoryData(
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
    String antecedentesFamiliaresDetalle) {
}
