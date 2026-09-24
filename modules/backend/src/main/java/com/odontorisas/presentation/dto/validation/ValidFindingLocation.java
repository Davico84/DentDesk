package com.odontorisas.presentation.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Validación <b>condicional</b> de un hallazgo del odontograma (design.md D9, {@code
 * add-dental-chart}): el alcance del {@code type} determina qué forma de {@code location} es
 * válida, el subtipo, el grado y la condición de color dependen todos de {@code type} a la vez —
 * mismo patrón que {@link ValidDiagnosisLocation}, pero con más campos cruzados.
 */
@Documented
@Constraint(validatedBy = ValidFindingLocationValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface ValidFindingLocation {
    String message() default "El hallazgo no es válido.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
