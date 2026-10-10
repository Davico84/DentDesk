package com.odontorisas.presentation.dto;

import com.odontorisas.common.NoteTextField;
import com.odontorisas.service.consultationnote.CorrectionView;

import java.time.Instant;

public record CorrectionResponse(
    Long id,
    NoteTextField field,
    String correctedText,
    String reason,
    Instant createdAt,
    Long createdBy) {

    public static CorrectionResponse from(CorrectionView v) {
        return new CorrectionResponse(v.id(), v.field(), v.correctedText(), v.reason(), v.createdAt(), v.createdBy());
    }
}
