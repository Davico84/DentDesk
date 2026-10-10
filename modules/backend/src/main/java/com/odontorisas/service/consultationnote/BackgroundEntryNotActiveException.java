package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** Una baja cierra un alta vigente del mismo paciente y categoría, registrada en una nota ya sellada (design.md D7). */
public class BackgroundEntryNotActiveException extends BusinessException {
    public BackgroundEntryNotActiveException() {
        super("Ese antecedente no está vigente: no se puede dar de baja.", HttpStatus.CONFLICT, "background-entry-not-active");
    }
}
