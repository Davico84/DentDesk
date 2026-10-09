package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** DS 009-2017-SA art. 47 c excluye la omisión de registro (design.md D6). */
public class CorrectionOfOmissionException extends BusinessException {
    public CorrectionOfOmissionException() {
        super("Ese campo estaba vacío: una omisión no se corrige como error material.", HttpStatus.BAD_REQUEST, "correction-of-omission");
    }
}
