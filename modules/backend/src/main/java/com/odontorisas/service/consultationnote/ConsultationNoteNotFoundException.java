package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** La nota no existe en la clínica activa (o es de otra clínica: el aislamiento responde como si no existiera). */
public class ConsultationNoteNotFoundException extends BusinessException {
    public ConsultationNoteNotFoundException() {
        super("La nota de consulta no existe.", HttpStatus.NOT_FOUND, "consultation-note-not-found");
    }
}
