package com.odontorisas.service.consultationnote;

import com.odontorisas.common.DiagnosisLocation;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.persistence.entity.ConsultationNoteDiagnosis;

import java.time.Instant;

public record NoteDiagnosisView(
    Long id,
    Long noteId,
    String cie10Code,
    DiagnosisLocation ubicacion,
    Integer fdiNumber,
    TipoDiagnostico tipoDiagnostico,
    Instant createdAt,
    Long createdBy) {

    public static NoteDiagnosisView from(ConsultationNoteDiagnosis d) {
        return new NoteDiagnosisView(d.getId(), d.getNoteId(), d.getCie10Code(), d.getUbicacion(),
            d.getFdiNumber(), d.getTipoDiagnostico(), d.getCreatedAt(), d.getCreatedBy());
    }
}
