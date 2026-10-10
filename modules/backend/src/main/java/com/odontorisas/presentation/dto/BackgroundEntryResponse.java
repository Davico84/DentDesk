package com.odontorisas.presentation.dto;

import com.odontorisas.common.BackgroundCategory;
import com.odontorisas.common.BackgroundEntryKind;
import com.odontorisas.service.consultationnote.BackgroundEntryView;

import java.time.Instant;

public record BackgroundEntryResponse(
    Long id,
    BackgroundCategory category,
    BackgroundEntryKind kind,
    String description,
    Long closesEntryId,
    Instant createdAt,
    Long createdBy) {

    public static BackgroundEntryResponse from(BackgroundEntryView v) {
        return new BackgroundEntryResponse(v.id(), v.category(), v.kind(), v.description(), v.closesEntryId(),
            v.createdAt(), v.createdBy());
    }
}
