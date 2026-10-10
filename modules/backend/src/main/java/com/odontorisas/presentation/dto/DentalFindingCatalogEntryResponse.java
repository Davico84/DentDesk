package com.odontorisas.presentation.dto;

import com.odontorisas.common.ColorBehavior;
import com.odontorisas.common.FindingType;
import com.odontorisas.common.LocationKind;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Una fila del catálogo de 38 hallazgos, aplanado por par {@code (type, subtype)} (design.md D6,
 * NTS 188 6.1.1-6.1.38). El frontend lo consume tal cual para pintar el selector de hallazgos y el
 * odontograma — no reinventa alcance, comportamiento de color ni sigla; los recibe por contrato.
 */
public record DentalFindingCatalogEntryResponse(
    @Schema(description = "Uno de los 38 tipos del catálogo.")
    FindingType type,

    @Schema(description = "Código del subtipo elegido dentro del tipo. \"\" si el tipo no tiene subtipos.")
    String subtype,

    @Schema(description = "Sigla oficial NTS 188 que se imprime en el recuadro de la pieza. "
        + "NO es única entre filas (colisiona 'M' y 'S'): la clave siempre es (type, subtype).",
        nullable = true)
    String acronym,

    @Schema(description = "SURFACE | TOOTH | BETWEEN_TEETH | TOOTH_RANGE | ARCH — determina la "
        + "variante de localización que admite este tipo.")
    LocationKind scope,

    @Schema(description = "SIEMPRE_AZUL | SIEMPRE_ROJO | SEGUN_ESTADO.")
    ColorBehavior colorBehavior,

    @Schema(description = "Si es true, el hallazgo exige un grado (1-3). Solo movilidad patológica.")
    boolean requiresGrade,

    @Schema(description = "Si es true, el tipo obliga a llenar una entrada en Especificaciones del bloque.")
    boolean requiresSpecification) {
}
