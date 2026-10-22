package com.odontorisas.presentation.dto;

import com.odontorisas.service.consultationnote.NoteFindingView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Un hallazgo del odontograma atendido en la nota, con la descripción guardada al marcarlo. */
public record NoteFindingResponse(
    Long id,
    @Schema(description = "El hallazgo del odontograma citado.")
    Long findingId,
    @Schema(description = "Descripción guardada al marcarlo; la nota sellada se lee con ella.")
    String description,
    Instant createdAt,
    Long createdBy) {

    public static NoteFindingResponse from(NoteFindingView v) {
        return new NoteFindingResponse(v.id(), v.findingId(), v.description(), v.createdAt(), v.createdBy());
    }
}
