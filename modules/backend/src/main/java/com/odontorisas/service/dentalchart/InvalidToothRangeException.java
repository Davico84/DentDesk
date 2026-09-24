package com.odontorisas.service.dentalchart;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Un {@code TOOTH_RANGE} con menos de dos piezas, con una pieza repetida, o con una pieza FDI
 * inválida (400 Bad Request). Dos invariantes de cardinalidad entre filas que ningún {@code CHECK}
 * de SQL puede expresar — las valida el servicio con el hallazgo bloqueado (design.md D2).
 */
public class InvalidToothRangeException extends BusinessException {
    public InvalidToothRangeException(String reason) {
        super(reason, HttpStatus.BAD_REQUEST, "dental-chart-invalid-tooth-range");
    }
}
