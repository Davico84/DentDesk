package com.odontorisas.service.dentalchart;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

public class DentalChartFindingNotFoundException extends BusinessException {
    public DentalChartFindingNotFoundException() {
        super("No se ha encontrado el hallazgo.", HttpStatus.NOT_FOUND, "dental-chart-finding-not-found");
    }
}
