package com.odontorisas.presentation.dto;

import com.odontorisas.common.BackgroundCategory;
import com.odontorisas.service.consultationnote.ActiveBackgroundView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Antecedentes vigentes del paciente, cada uno con su origen (design.md D8). */
public record ActiveBackgroundResponse(List<Item> items) {

    public record Item(
        BackgroundCategory category,
        @Schema(description = "HISTORIA | NOTA | FICHA. FICHA = registrado al dar de alta, sin autoría clínica.")
        ActiveBackgroundView.Source source,
        String description,
        @Schema(description = "Fecha de la Historia sellada o de la atención; null para FICHA.")
        Instant recordedAt,
        Long noteId,
        @Schema(description = "El alta (solo origen NOTA): es lo que una baja referencia.")
        Long entryId) {
    }

    public static ActiveBackgroundResponse from(ActiveBackgroundView v) {
        return new ActiveBackgroundResponse(v.items().stream()
            .map(i -> new Item(i.category(), i.source(), i.description(), i.recordedAt(), i.noteId(), i.entryId()))
            .toList());
    }
}
