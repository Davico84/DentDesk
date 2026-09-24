package com.odontorisas.service.dentalchart;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * No hay odontograma para ese paciente en la clínica activa (404 Not Found). Igual que {@code
 * HistoriaClinicaNotFoundException}: también se responde 404 si pertenece a otra clínica — el
 * aislamiento lo hace invisible, sin revelar que existe en otra parte.
 */
public class DentalChartBlockNotFoundException extends BusinessException {
    public DentalChartBlockNotFoundException() {
        super("No se ha encontrado el odontograma.", HttpStatus.NOT_FOUND, "dental-chart-block-not-found");
    }
}
