package com.odontorisas.service.consultationnote;

import com.odontorisas.common.NoteTextField;
import com.odontorisas.persistence.entity.ConsultationNoteCorrection;

import java.time.Instant;

public record CorrectionView(
    Long id,
    Long noteId,
    NoteTextField field,
    String correctedText,
    String reason,
    Instant createdAt,
    Long createdBy) {

    public static CorrectionView from(ConsultationNoteCorrection c) {
        return new CorrectionView(c.getId(), c.getNoteId(), c.getField(), c.getCorrectedText(),
            c.getReason(), c.getCreatedAt(), c.getCreatedBy());
    }
}
