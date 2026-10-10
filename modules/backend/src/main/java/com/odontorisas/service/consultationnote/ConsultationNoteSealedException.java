package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** La nota ya está {@code SELLADA}: no se edita, no se descarta ni se vuelve a sellar (design.md D2). Rechazo server-side siempre. */
public class ConsultationNoteSealedException extends BusinessException {
    public ConsultationNoteSealedException() {
        super("La nota de consulta está sellada; no se puede modificar.", HttpStatus.CONFLICT, "consultation-note-sealed");
    }
}
