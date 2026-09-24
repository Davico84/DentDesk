package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo compartido por especificaciones y observaciones (design.md D5): {@code text == null} es
 * una afirmación válida ("revisado, nada que anotar"), no un olvido — ambos endpoints marcan su
 * {@code *Reviewed} en {@code true} igual con o sin texto.
 */
public record DentalChartTextRequest(
    @Schema(description = "Texto libre. Ausente o null es válido: revisado, sin nada que anotar.", nullable = true)
    String text) {
}
