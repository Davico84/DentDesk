package com.odontorisas.clinicalhistory;

import com.odontorisas.common.DiagnosisLocation;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.presentation.dto.DiagnosisRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validación condicional de ubicación de diagnóstico (design.md D5): {@code PIEZA} exige un FDI
 * del conjunto real; {@code GENERAL} lo rechaza. Mismo estilo que
 * {@code GuardianRequiredForMinorValidatorTest}.
 */
class ValidDiagnosisLocationValidatorTest {

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

    private Set<String> fieldsInError(DiagnosisRequest request) {
        Set<ConstraintViolation<DiagnosisRequest>> violations = validator.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void a_general_diagnosis_without_fdi_is_accepted() {
        var request = new DiagnosisRequest("A00", DiagnosisLocation.GENERAL, null, TipoDiagnostico.PRESUNTIVO);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void a_piece_diagnosis_with_a_valid_fdi_is_accepted() {
        var request = new DiagnosisRequest("A00", DiagnosisLocation.PIEZA, 36, TipoDiagnostico.DEFINITIVO);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void a_piece_diagnosis_without_fdi_is_flagged_on_fdi_number() {
        var request = new DiagnosisRequest("A00", DiagnosisLocation.PIEZA, null, TipoDiagnostico.DEFINITIVO);
        assertThat(fieldsInError(request)).contains("fdiNumber");
    }

    @Test
    void a_piece_diagnosis_with_a_number_outside_the_valid_set_is_flagged() {
        assertThat(fieldsInError(new DiagnosisRequest("A00", DiagnosisLocation.PIEZA, 19, TipoDiagnostico.DEFINITIVO)))
            .contains("fdiNumber");
        assertThat(fieldsInError(new DiagnosisRequest("A00", DiagnosisLocation.PIEZA, 20, TipoDiagnostico.DEFINITIVO)))
            .contains("fdiNumber");
        assertThat(fieldsInError(new DiagnosisRequest("A00", DiagnosisLocation.PIEZA, 99, TipoDiagnostico.DEFINITIVO)))
            .contains("fdiNumber");
    }

    @Test
    void a_general_diagnosis_with_fdi_is_flagged_on_fdi_number() {
        var request = new DiagnosisRequest("A00", DiagnosisLocation.GENERAL, 36, TipoDiagnostico.DEFINITIVO);
        assertThat(fieldsInError(request)).contains("fdiNumber");
    }

    @Test
    void a_diagnosis_without_a_type_is_rejected() {
        var request = new DiagnosisRequest("A00", DiagnosisLocation.GENERAL, null, null);
        assertThat(fieldsInError(request)).contains("tipoDiagnostico");
    }
}
