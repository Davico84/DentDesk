package com.odontorisas.presentation.dto.validation;

import com.odontorisas.common.DiagnosisLocation;
import com.odontorisas.common.FdiTooth;
import com.odontorisas.presentation.dto.DiagnosisRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Comprueba {@link ValidDiagnosisLocation}: {@code PIEZA} exige un {@code fdiNumber} dentro del
 * conjunto real de piezas ({@link FdiTooth}); {@code GENERAL} rechaza cualquier {@code fdiNumber}.
 * Si {@code ubicacion} es {@code null}, no reporta nada aquí — lo cubre el propio {@code @NotNull}
 * del campo.
 */
public class ValidDiagnosisLocationValidator
    implements ConstraintValidator<ValidDiagnosisLocation, DiagnosisRequest> {

    @Override
    public boolean isValid(DiagnosisRequest request, ConstraintValidatorContext context) {
        if (request == null || request.ubicacion() == null) {
            return true;
        }
        Integer fdi = request.fdiNumber();
        if (request.ubicacion() == DiagnosisLocation.PIEZA) {
            if (fdi == null) {
                return reject(context, "fdiNumber", "La pieza (número FDI) es obligatoria para esta ubicación.");
            }
            if (!FdiTooth.isValid(fdi)) {
                return reject(context, "fdiNumber", "El número FDI no corresponde a ninguna pieza válida.");
            }
            return true;
        }
        // GENERAL
        if (fdi != null) {
            return reject(context, "fdiNumber", "Un diagnóstico general no admite número de pieza.");
        }
        return true;
    }

    private boolean reject(ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
            .addPropertyNode(field)
            .addConstraintViolation();
        return false;
    }
}
