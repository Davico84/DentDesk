package com.odontorisas.service.staff;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * El número COP ya figura en la credencial de otro usuario (409). Texto fijo: no dice quién, en qué
 * clínica ni repite el número (design.md D1), y no lleva la causa SQL, que trae el valor.
 */
public class CredentialNumberTakenException extends BusinessException {
    public CredentialNumberTakenException() {
        super("Ese número de colegiatura ya está registrado en otra cuenta.", HttpStatus.CONFLICT,
            "credential-number-taken");
    }
}
