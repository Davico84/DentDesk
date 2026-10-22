package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** Solo se citan hallazgos de un odontograma sellado: la descripción guardada tiene que seguir siendo cierta (design.md D1). */
public class DentalChartFindingNotSealedException extends BusinessException {
    public DentalChartFindingNotSealedException() {
        super("El hallazgo pertenece a un odontograma que aún no está sellado.", HttpStatus.CONFLICT, "dental-chart-finding-not-sealed");
    }
}
