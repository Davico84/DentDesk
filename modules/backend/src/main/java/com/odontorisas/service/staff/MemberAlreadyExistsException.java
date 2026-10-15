package com.odontorisas.service.staff;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/** El correo ya pertenece a la organización activa. */
public class MemberAlreadyExistsException extends BusinessException {
    public MemberAlreadyExistsException(String email) {
        super("El correo ya pertenece a esta organización: " + email,
            HttpStatus.CONFLICT, "member-already-exists");
    }
}
