package com.odontorisas.presentation.dto;

import com.odontorisas.common.BackgroundCategory;
import com.odontorisas.common.BackgroundEntryKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * Un asiento de antecedente (design.md D7). ALTA: {@code category} y {@code description}
 * obligatorias, sin {@code closesEntryId}. BAJA: {@code closesEntryId} obligatorio, sin
 * {@code category} (la pone el alta que se cierra); {@code description} opcional.
 */
public record BackgroundEntryRequest(
    @NotNull
    @Schema(description = "ALTA | BAJA")
    BackgroundEntryKind kind,
    @Schema(description = "ALERGIA_RAM | ANTECEDENTE_PATOLOGICO | MEDICACION. Solo en el alta.")
    BackgroundCategory category,
    @Schema(description = "Obligatoria en el alta; en la baja, por qué se da de baja.")
    String description,
    @Schema(description = "El alta vigente que se da de baja. Solo en la baja.")
    Long closesEntryId) {

    @AssertTrue(message = "Un alta lleva categoría y descripción; una baja, el antecedente que cierra.")
    @Schema(hidden = true)
    public boolean isKindConsistent() {
        if (kind == null) {
            return true;
        }
        if (kind == BackgroundEntryKind.ALTA) {
            return category != null && description != null && !description.isBlank() && closesEntryId == null;
        }
        return closesEntryId != null && category == null;
    }
}
