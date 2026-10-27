package com.odontorisas.service.staff;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * La fecha "habilitado hasta" es anterior a hoy en la hora de Perú (400). Va en el servicio y no como
 * Bean Validation: {@code @FutureOrPresent} usaría la zona del servidor, no la de Perú (design.md D3).
 */
public class LicensedUntilInPastException extends BusinessException {
    public LicensedUntilInPastException() {
        super("La fecha de habilitación debe ser hoy o posterior.", HttpStatus.BAD_REQUEST, "licensed-until-in-past");
    }
}
