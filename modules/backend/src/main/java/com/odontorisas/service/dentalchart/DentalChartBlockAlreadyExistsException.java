package com.odontorisas.service.dentalchart;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * El paciente ya tiene un odontograma inicial abierto o sellado en esta clínica (409 Conflict):
 * uno solo, de por vida (ADR-0012 D7, ADR-0017 D3). Se lanza tanto por el pre-chequeo como por la
 * violación de los índices únicos parciales bajo creación concurrente — la unicidad la garantiza
 * el dato, el pre-chequeo es solo la vía feliz (design.md D3).
 */
public class DentalChartBlockAlreadyExistsException extends BusinessException {
    public DentalChartBlockAlreadyExistsException() {
        super("El paciente ya tiene un odontograma inicial en esta clínica.",
            HttpStatus.CONFLICT, "dental-chart-block-already-exists");
    }
}
