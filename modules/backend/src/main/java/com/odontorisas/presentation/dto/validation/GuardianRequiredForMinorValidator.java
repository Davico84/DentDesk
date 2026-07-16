package com.odontorisas.presentation.dto.validation;

import com.odontorisas.common.Ages;
import com.odontorisas.presentation.dto.PatientRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Comprueba la regla de {@link GuardianRequiredForMinor}: si {@code birthDate} indica menor de
 * edad, exige nombre, parentesco y teléfono del responsable, reportando la falta <b>por campo</b>.
 */
public class GuardianRequiredForMinorValidator
    implements ConstraintValidator<GuardianRequiredForMinor, PatientRequest> {

    @Override
    public boolean isValid(PatientRequest request, ConstraintValidatorContext context) {
        if (request == null || !Ages.isMinor(request.birthDate())) {
            return true; // adulto (o sin fecha: de eso se encarga @NotNull en birthDate)
        }
        boolean valid = true;
        valid &= requirePresent(hasText(request.guardianName()), "guardianName", context, valid);
        valid &= requirePresent(request.guardianRelationship() != null, "guardianRelationship", context, valid);
        valid &= requirePresent(hasText(request.guardianPhone()), "guardianPhone", context, valid);
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
        context.buildConstraintViolationWithTemplate("Obligatorio para un paciente menor de edad.")
            .addPropertyNode(field)
            .addConstraintViolation();
        return false;
    }
}
