package com.odontorisas.service.consultationnote;

import com.odontorisas.common.BackgroundCategory;
import com.odontorisas.common.BackgroundEntryKind;

/**
 * Un asiento de antecedente (design.md D7). En el alta, {@code description} es obligatoria y
 * {@code closesEntryId} nulo; en la baja, {@code closesEntryId} es obligatorio y la descripción es
 * opcional (por qué se da de baja). En la baja la categoría la pone el alta que se cierra.
 */
public record BackgroundEntryData(
    BackgroundEntryKind kind,
    BackgroundCategory category,
    String description,
    Long closesEntryId) {
}
