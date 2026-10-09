package com.odontorisas.service.dentalchart;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Ya existe un hallazgo con el mismo tipo, subtipo y localización en este odontograma (409
 * Conflict) — spec: <i>El mismo hallazgo no se duplica</i>. Para {@code SURFACE}/{@code
 * TOOTH}/{@code BETWEEN_TEETH}/{@code ARCH} se lanza por la violación del índice único parcial
 * correspondiente (design.md D3); para {@code TOOTH_RANGE}, que no tiene índice único posible, por
 * el chequeo del servicio con el bloque bloqueado.
 */
public class DentalChartFindingAlreadyExistsException extends BusinessException {
    public DentalChartFindingAlreadyExistsException() {
        super("Ya existe un hallazgo igual en este odontograma.",
            HttpStatus.CONFLICT, "dental-chart-finding-already-exists");
    }
}
