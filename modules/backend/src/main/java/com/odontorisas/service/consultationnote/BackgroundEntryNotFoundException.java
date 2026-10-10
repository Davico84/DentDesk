package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** El asiento no existe en la nota o en la clínica activa. */
public class BackgroundEntryNotFoundException extends BusinessException {
    public BackgroundEntryNotFoundException() {
        super("El antecedente no existe.", HttpStatus.NOT_FOUND, "background-entry-not-found");
    }
}
