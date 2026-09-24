package com.odontorisas.service.dentalchart;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * El odontograma ya está {@code SEALED}: es un documento fechado, no un estado mutable (ADR-0012
 * D3). El rechazo es <b>server-side siempre</b>, aunque la petición llegue directa a la API sin
 * pasar por la interfaz (spec: rechazar por API directa).
 */
public class DentalChartBlockSealedException extends BusinessException {
    public DentalChartBlockSealedException() {
        super("El odontograma ya está sellado; no se puede modificar.",
            HttpStatus.CONFLICT, "dental-chart-block-sealed");
    }
}
