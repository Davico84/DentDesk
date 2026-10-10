package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** Regla del tipo R (NTS 139 §4.2.9, design.md D5): el código debe figurar en la Historia o en una nota sellada del paciente. */
public class RepeatedDiagnosisWithoutPriorException extends BusinessException {
    public RepeatedDiagnosisWithoutPriorException() {
        super("Un diagnóstico repetitivo requiere que ese código se haya registrado antes al paciente.", HttpStatus.BAD_REQUEST, "repeated-diagnosis-without-prior");
    }
}
