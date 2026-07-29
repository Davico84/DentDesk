package com.odontorisas.service.clinicalhistory;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** No hay un diagnóstico con ese id en esa Historia Clínica (404 Not Found). */
public class DiagnosisNotFoundException extends BusinessException {
    public DiagnosisNotFoundException() {
        super("No se ha encontrado el diagnóstico.", HttpStatus.NOT_FOUND, "clinical-history-diagnosis-not-found");
    }
}
