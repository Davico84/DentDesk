package com.odontorisas.service.consultationnote;

import com.odontorisas.common.NoteTextField;

/** Corrección de error material: campo (la "ubicación del error"), texto corregido y motivo (design.md D6). */
public record CorrectionData(NoteTextField field, String correctedText, String reason) {
}
