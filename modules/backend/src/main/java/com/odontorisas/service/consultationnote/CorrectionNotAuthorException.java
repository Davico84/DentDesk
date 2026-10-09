package com.odontorisas.service.consultationnote;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** DS 009-2017-SA art. 47 c: el profesional corrige <i>su</i> registro (design.md D6). */
public class CorrectionNotAuthorException extends BusinessException {
    public CorrectionNotAuthorException() {
        super("Solo quien selló la nota puede corregirla.", HttpStatus.FORBIDDEN, "correction-not-author");
    }
}
