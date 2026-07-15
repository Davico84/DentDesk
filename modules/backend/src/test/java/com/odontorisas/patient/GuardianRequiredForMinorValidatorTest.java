package com.odontorisas.patient;

import com.odontorisas.common.GuardianRelationship;
import com.odontorisas.presentation.dto.PatientRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validación condicional del menor (design.md D2): si la fecha indica menor de edad, el
 * responsable es obligatorio, y la falta se reporta <b>por campo</b> para que el 400 la pinte
 * junto al input.
 */
class GuardianRequiredForMinorValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private PatientRequest request(LocalDate birthDate, String guardianName, GuardianRelationship guardianRelationship, String guardianPhone) {
        return new PatientRequest("Nombre Válido", null, null, birthDate, null, null, null, null,
            guardianName, guardianRelationship, null, guardianPhone,
            null, null, null, null, null, null);
    }

    private LocalDate minorBirthDate() {
        return LocalDate.now().minusYears(10);
    }

    private LocalDate adultBirthDate() {
        return LocalDate.now().minusYears(30);
    }

    @Test
    void a_minor_without_guardian_is_rejected_with_a_violation_per_field() {
        Set<ConstraintViolation<PatientRequest>> violations =
            validator.validate(request(minorBirthDate(), null, null, null));

        assertThat(violations)
            .extracting(v -> v.getPropertyPath().toString())
            .contains("guardianName", "guardianRelationship", "guardianPhone");
    }

    @Test
    void a_minor_with_guardian_is_accepted() {
        Set<ConstraintViolation<PatientRequest>> violations =
            validator.validate(request(minorBirthDate(), "María", GuardianRelationship.MOTHER, "+51999888777"));

        assertThat(violations).isEmpty();
    }

    @Test
    void an_adult_without_guardian_is_accepted() {
        Set<ConstraintViolation<PatientRequest>> violations =
            validator.validate(request(adultBirthDate(), null, null, null));

        assertThat(violations).isEmpty();
    }

    @Test
    void a_minor_missing_only_the_phone_is_flagged_only_on_the_phone() {
        Set<ConstraintViolation<PatientRequest>> violations =
            validator.validate(request(minorBirthDate(), "María", GuardianRelationship.MOTHER, null));

        assertThat(violations)
            .extracting(v -> v.getPropertyPath().toString())
            .containsExactly("guardianPhone");
    }
}
