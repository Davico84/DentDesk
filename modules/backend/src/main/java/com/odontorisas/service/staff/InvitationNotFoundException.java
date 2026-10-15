package com.odontorisas.service.staff;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** La invitación no existe o es de otra organización (404): no se revela cuál de las dos. */
public class InvitationNotFoundException extends BusinessException {
    public InvitationNotFoundException() {
        super("La invitación no existe.", HttpStatus.NOT_FOUND, "invitation-not-found");
    }
}
