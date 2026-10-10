package com.odontorisas.presentation.dto;

import com.odontorisas.service.dentalchart.AddFindingResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resultado de agregar o editar un hallazgo, con el aviso <b>no bloqueante</b> de incoherencia
 * dentición↔edad (design.md D9/INV-15, `4.2`): viaja como dato de la respuesta, nunca como error —
 * una pieza temporal retenida en un adulto es rara pero real, así que se registra igual.
 */
public record DentalChartFindingCreatedResponse(
    DentalChartFindingResponse finding,

    @Schema(description = "true si el hallazgo toca una pieza temporal en un paciente adulto. "
        + "Informativo, no bloquea el registro.")
    boolean denticionWarning) {

    public static DentalChartFindingCreatedResponse from(AddFindingResult r) {
        return new DentalChartFindingCreatedResponse(DentalChartFindingResponse.from(r.finding()), r.denticionWarning());
    }
}
