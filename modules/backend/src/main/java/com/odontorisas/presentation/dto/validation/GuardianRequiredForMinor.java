package com.odontorisas.presentation.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Validación <b>condicional</b> (design.md D2): si la fecha de nacimiento indica que el paciente
 * es <b>menor de edad</b>, los datos del responsable (nombre, parentesco y teléfono) pasan a ser
 * obligatorios. No se puede expresar con un {@code @NotNull} por campo —depende de otro campo—,
 * así que es una restricción <b>a nivel de objeto</b>.
 *
 * <p>Al fallar reporta la violación sobre <b>cada campo</b> del responsable que falte, de modo que
 * el {@code 400} lleve el detalle por campo que el frontend pinta junto al input. El
 * {@code guardianDocument} NO es obligatorio (design.md D2).
 */
@Documented
@Constraint(validatedBy = GuardianRequiredForMinorValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface GuardianRequiredForMinor {
    String message() default "Los datos del responsable son obligatorios para un paciente menor de edad.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
