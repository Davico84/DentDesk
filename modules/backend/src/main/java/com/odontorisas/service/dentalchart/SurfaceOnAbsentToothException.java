package com.odontorisas.service.dentalchart;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Se intentó registrar un hallazgo de superficie o de pieza sobre un diente marcado como ausente
 * ({@code PIEZA_AUSENTE}, 6.1.20) en el mismo odontograma (409 Conflict): una pieza que no está no
 * puede tener caries ni corona. ⚠️ El implante ({@code IMPLANTE_DENTAL}, 6.115) es la excepción
 * deliberada: coexiste con la ausencia y no dispara este rechazo (ADR-0017 D7).
 */
public class SurfaceOnAbsentToothException extends BusinessException {
    public SurfaceOnAbsentToothException(int tooth) {
        super("La pieza " + tooth + " está marcada como ausente; no admite este hallazgo.",
            HttpStatus.CONFLICT, "dental-chart-surface-on-absent-tooth");
    }
}
