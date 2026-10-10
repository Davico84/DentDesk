package com.odontorisas.presentation.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Validación <b>condicional</b> (update-patients D2, ADR-0010): si el paciente <b>no</b> registra un
 * teléfono propio, el contacto de emergencia (nombre, teléfono y relación) pasa a ser obligatorio;
 * si tiene teléfono propio, sigue siendo opcional. Depende de otro campo, así que es una restricción
 * <b>a nivel de objeto</b>, igual que {@link GuardianRequiredForMinor}.
 *
 * <p>Al fallar reporta la violación sobre <b>cada campo</b> del contacto de emergencia que falte,
 * de modo que el {@code 400} lleve el detalle por campo que el frontend pinta junto al input.
 */
@Documented
@Constraint(validatedBy = EmergencyContactRequiredWithoutPhoneValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface EmergencyContactRequiredWithoutPhone {
    String message() default "El contacto de emergencia es obligatorio si el paciente no tiene teléfono propio.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
