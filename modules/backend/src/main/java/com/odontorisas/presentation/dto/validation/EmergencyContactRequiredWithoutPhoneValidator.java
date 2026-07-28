package com.odontorisas.presentation.dto.validation;

import com.odontorisas.presentation.dto.PatientRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Comprueba la regla de {@link EmergencyContactRequiredWithoutPhone}: si no hay teléfono propio,
 * exige nombre, teléfono y relación del contacto de emergencia, reportando la falta <b>por campo</b>.
 */
public class EmergencyContactRequiredWithoutPhoneValidator
    implements ConstraintValidator<EmergencyContactRequiredWithoutPhone, PatientRequest> {

    @Override
    public boolean isValid(PatientRequest request, ConstraintValidatorContext context) {
        if (request == null || hasText(request.phone())) {
            return true; // tiene teléfono propio: el contacto de emergencia es opcional
        }
        boolean valid = true;
        valid &= requirePresent(hasText(request.emergencyContactName()), "emergencyContactName", context, valid);
        valid &= requirePresent(hasText(request.emergencyContactPhone()), "emergencyContactPhone", context, valid);
        valid &= requirePresent(hasText(request.emergencyContactRelationship()), "emergencyContactRelationship", context, valid);
        return valid;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean requirePresent(boolean present, String field, ConstraintValidatorContext context, boolean firstStillValid) {
        if (present) {
            return true;
        }
        if (firstStillValid) {
            // La primera violación desactiva el mensaje por defecto a nivel de clase; cada campo
            // aporta el suyo, anclado a su propiedad, para que errors[] lo pinte junto al input.
            context.disableDefaultConstraintViolation();
        }
        context.buildConstraintViolationWithTemplate("Obligatorio si el paciente no tiene teléfono propio.")
            .addPropertyNode(field)
            .addConstraintViolation();
        return false;
    }
}
