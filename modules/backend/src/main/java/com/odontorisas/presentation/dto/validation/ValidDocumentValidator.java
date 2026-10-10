package com.odontorisas.presentation.dto.validation;

import com.odontorisas.common.DocumentType;
import com.odontorisas.presentation.dto.PatientRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Verifica el emparejamiento tipo↔número y el formato del documento según el tipo ({@link ValidDocument}). */
public class ValidDocumentValidator implements ConstraintValidator<ValidDocument, PatientRequest> {

    @Override
    public boolean isValid(PatientRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        DocumentType type = request.documentType();
        String document = request.document();
        boolean hasNumber = document != null && !document.isBlank();

        // Sin documento: válido (es opcional).
        if (type == null && !hasNumber) {
            return true;
        }

        // Emparejamiento: o ambos o ninguno.
        if (type == null) {
            return violation(context, "documentType", "Indica el tipo de documento.");
        }
        if (!hasNumber) {
            return violation(context, "document", "Indica el número de documento.");
        }

        // Formato según el tipo (la regla vive en el enum).
        if (!type.matches(document.trim())) {
            return violation(context, "document", "El documento no tiene el formato válido para el tipo " + type + ".");
        }
        return true;
    }

    private boolean violation(ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
            .addPropertyNode(field)
            .addConstraintViolation();
        return false;
    }
}
