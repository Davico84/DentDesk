package com.odontorisas.service.consultationnote;

import com.odontorisas.persistence.entity.ConsultationNoteFinding;

import java.time.Instant;

/** Un hallazgo del odontograma atendido en la nota, con la descripción guardada al marcarlo. */
public record NoteFindingView(
    Long id,
    Long noteId,
    Long findingId,
    String description,
    Instant createdAt,
    Long createdBy) {

    public static NoteFindingView from(ConsultationNoteFinding f) {
        return new NoteFindingView(f.getId(), f.getNoteId(), f.getFindingId(), f.getDescription(),
            f.getCreatedAt(), f.getCreatedBy());
    }
}
