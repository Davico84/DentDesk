package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** El diagnóstico no pertenece a la nota indicada. */
public class NoteDiagnosisNotFoundException extends BusinessException {
    public NoteDiagnosisNotFoundException() {
        super("El diagnóstico no existe en la nota.", HttpStatus.NOT_FOUND, "consultation-note-diagnosis-not-found");
    }
}
