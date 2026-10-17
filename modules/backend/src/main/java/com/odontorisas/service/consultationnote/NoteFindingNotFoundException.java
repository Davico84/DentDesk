package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** El hallazgo no existe, es de otra clínica o de otro paciente, o no está marcado en la nota: 404 anti-enumeración (design.md D1). */
public class NoteFindingNotFoundException extends BusinessException {
    public NoteFindingNotFoundException() {
        super("El hallazgo no existe para el paciente de la nota.", HttpStatus.NOT_FOUND, "consultation-note-finding-not-found");
    }
}
