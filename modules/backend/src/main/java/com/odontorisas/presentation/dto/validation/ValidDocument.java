package com.odontorisas.presentation.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Valida el documento del paciente (design.md D8), reportando <b>por campo</b>:
 * <ul>
 *   <li><b>Emparejado con su tipo</b>: o ambos ({@code documentType} + {@code document}) o
 *       ninguno. Un número sin tipo no se puede validar; un tipo sin número no identifica.</li>
 *   <li><b>Formato según el tipo</b>: DNI = 8 dígitos, CE = 9–12, pasaporte = alfanumérico 6–12
 *       (la regla vive en {@code DocumentType}).</li>
 * </ul>
 * El documento sigue siendo <b>opcional</b> (un bebé sin DNI): si ambos están vacíos, es válido.
 */
@Documented
@Constraint(validatedBy = ValidDocumentValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface ValidDocument {
    String message() default "Documento inválido.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
