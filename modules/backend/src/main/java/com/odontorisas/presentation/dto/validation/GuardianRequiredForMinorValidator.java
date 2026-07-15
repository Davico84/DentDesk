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
        valid &= requireField(request.guardianName(), "guardianName", context, valid);
        valid &= requireField(request.guardianRelationship(), "guardianRelationship", context, valid);
        valid &= requireField(request.guardianPhone(), "guardianPhone", context, valid);
        return valid;
    }

    private boolean requireField(String value, String field, ConstraintValidatorContext context, boolean firstStillValid) {
        if (value != null && !value.isBlank()) {
            return true;
        }
        if (firstStillValid) {
            // La primera violación desactiva el mensaje por defecto a nivel de clase; cada campo
            // aporta el suyo, anclado a su propiedad, para que errors[] lo pinte junto al input.
            context.disableDefaultConstraintViolation();
        }
        context.buildConstraintViolationWithTemplate(
                "Obligatorio para un paciente menor de edad.")
            .addPropertyNode(field)
            .addConstraintViolation();
        return false;
    }
}
