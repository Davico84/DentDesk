package com.odontorisas.service.staff;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** Ya hay una invitación pendiente o vencida para ese correo en la organización (409). */
public class InvitationAlreadyPendingException extends BusinessException {
    public InvitationAlreadyPendingException() {
        super("Ya hay una invitación pendiente o vencida para este correo. Puedes reenviarla desde la lista de invitaciones.", HttpStatus.CONFLICT, "invitation-already-pending");
    }
}
