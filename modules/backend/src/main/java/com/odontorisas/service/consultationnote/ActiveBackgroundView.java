package com.odontorisas.service.consultationnote;

import com.odontorisas.common.BackgroundCategory;

import java.time.Instant;
import java.util.List;

/**
 * Antecedentes vigentes del paciente por categoría, con su origen (design.md D8). Se calcula en cada
 * lectura: no hay tabla de vigentes que pueda desincronizarse.
 */
public record ActiveBackgroundView(List<Item> items) {

    public enum Source {
        /** Texto de la Historia Clínica sellada. */
        HISTORIA,
        /** Alta de una nota sellada, sin baja. */
        NOTA,
        /** Anamnesis de la ficha del paciente: registrada al dar de alta, sin autoría clínica. */
        FICHA
    }

    /**
     * @param recordedAt {@code completed_at} de la Historia o {@code attended_at} de la nota;
     *                   {@code null} para {@link Source#FICHA}, que no tiene fecha clínica
     * @param entryId    el asiento (solo {@link Source#NOTA}): es lo que una baja referencia
     */
    public record Item(BackgroundCategory category, Source source, String description,
                       Instant recordedAt, Long noteId, Long entryId) {
    }
}
