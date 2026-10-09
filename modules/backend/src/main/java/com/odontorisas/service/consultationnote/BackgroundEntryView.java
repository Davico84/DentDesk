package com.odontorisas.service.consultationnote;

import com.odontorisas.common.BackgroundCategory;
import com.odontorisas.common.BackgroundEntryKind;
import com.odontorisas.persistence.entity.ClinicalBackgroundEntry;

import java.time.Instant;

public record BackgroundEntryView(
    Long id,
    Long noteId,
    BackgroundCategory category,
    BackgroundEntryKind kind,
    String description,
    Long closesEntryId,
    Instant createdAt,
    Long createdBy) {

    public static BackgroundEntryView from(ClinicalBackgroundEntry e) {
        return new BackgroundEntryView(e.getId(), e.getNoteId(), e.getCategory(), e.getKind(),
            e.getDescription(), e.getClosesEntryId(), e.getCreatedAt(), e.getCreatedBy());
    }
}
