package com.odontorisas.service.staff;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** Un administrador no puede cambiar el estado de su propia membresía (409). */
public class CannotChangeOwnStatusException extends BusinessException {
    public CannotChangeOwnStatusException() {
        super("No puedes cambiar el estado de tu propia cuenta.", HttpStatus.CONFLICT, "cannot-change-own-status");
    }
}
