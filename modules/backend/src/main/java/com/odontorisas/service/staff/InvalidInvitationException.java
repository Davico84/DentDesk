package com.odontorisas.service.auth;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidInvitationException extends BusinessException {
    public InvalidInvitationException() {
        super("La invitacion no es valida, expiro o ya fue utilizada.", HttpStatus.BAD_REQUEST, "invalid-invitation");
    }
}
