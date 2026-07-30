package com.odontorisas.service.clinicalhistory;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * El código CIE10 indicado no existe en el catálogo oficial MINSA (400 Bad Request) — no se puede
 * registrar un diagnóstico con un código que el catálogo no reconoce (spec: rechazar código
 * inexistente).
 */
public class Cie10CodeNotFoundException extends BusinessException {
    public Cie10CodeNotFoundException() {
        super("El código CIE10 indicado no existe en el catálogo.", HttpStatus.BAD_REQUEST, "cie10-code-not-found");
    }
}
