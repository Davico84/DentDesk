package com.odontorisas.service.staff;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** La invitación ya fue aceptada o revocada: no se puede reenviar ni revocar (409). */
public class InvitationNotPendingException extends BusinessException {
    public InvitationNotPendingException() {
        super("La invitación ya fue aceptada o revocada.", HttpStatus.CONFLICT, "invitation-not-pending");
    }
}
