package com.odontorisas.service.staff;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** La persona no es miembro de la organización activa (404). */
public class MemberNotFoundException extends BusinessException {
    public MemberNotFoundException() {
        super("La persona no pertenece a tu clínica.", HttpStatus.NOT_FOUND, "member-not-found");
    }
}
