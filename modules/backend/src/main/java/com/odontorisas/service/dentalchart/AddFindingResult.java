package com.odontorisas.service.dentalchart;

/**
 * El resultado de agregar un hallazgo, con el aviso <b>no bloqueante</b> de incoherencia
 * dentición↔edad (design.md D9, INV-15): una pieza temporal retenida en un adulto es rara pero
 * real, así que se registra igual — el aviso informa, no impide.
 */
public record AddFindingResult(DentalChartFindingView finding, boolean denticionWarning) {
}
