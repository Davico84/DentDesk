package com.odontorisas.service.clinicalhistory;

import com.odontorisas.common.DiagnosisLocation;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.persistence.entity.ClinicalHistoryDiagnosis;

import java.time.Instant;

public record DiagnosisView(
    Long id,
    Long historiaId,
    String cie10Code,
    DiagnosisLocation ubicacion,
    Integer fdiNumber,
    TipoDiagnostico tipoDiagnostico,
    Instant createdAt,
    Long createdBy) {

    public static DiagnosisView from(ClinicalHistoryDiagnosis d) {
        return new DiagnosisView(
            d.getId(), d.getHistoriaId(), d.getCie10Code(), d.getUbicacion(),
            d.getFdiNumber(), d.getTipoDiagnostico(), d.getCreatedAt(), d.getCreatedBy());
    }
}
