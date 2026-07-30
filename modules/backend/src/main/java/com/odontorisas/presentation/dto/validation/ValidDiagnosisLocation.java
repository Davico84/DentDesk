package com.odontorisas.presentation.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Validación <b>condicional</b> de ubicación de diagnóstico (design.md D5, `add-clinical-history`):
 * {@code PIEZA} exige un número FDI dentro del conjunto real de piezas (no un rango laxo);
 * {@code GENERAL} rechaza cualquier número FDI. Depende de dos campos a la vez, así que es una
 * restricción <b>a nivel de objeto</b> — mismo patrón que {@link GuardianRequiredForMinor} /
 * {@link EmergencyContactRequiredWithoutPhone}.
 */
@Documented
@Constraint(validatedBy = ValidDiagnosisLocationValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface ValidDiagnosisLocation {
    String message() default "La ubicación del diagnóstico no es válida.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
