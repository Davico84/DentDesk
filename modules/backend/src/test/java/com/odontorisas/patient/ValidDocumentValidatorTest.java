package com.odontorisas.patient;

import com.odontorisas.common.DocumentType;
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
 * Validación del documento (design.md D8): emparejamiento tipo↔número y formato según el tipo,
 * reportado por campo.
 */
class ValidDocumentValidatorTest {

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

    /**
     * Con teléfono propio: así {@code EmergencyContactRequiredWithoutPhone} (update-patients D2,
     * regla independiente de esta) no interfiere en tests que no la están probando.
     */
    private PatientRequest request(DocumentType type, String document) {
        return new PatientRequest("Adulto Válido", type, document, LocalDate.of(1990, 1, 1),
            null, "+51988877766", null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private Set<String> fieldsInError(PatientRequest request) {
        Set<ConstraintViolation<PatientRequest>> violations = validator.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void no_document_and_no_type_is_valid() {
        assertThat(validator.validate(request(null, null))).isEmpty();
    }

    @Test
    void a_valid_dni_is_accepted() {
        assertThat(validator.validate(request(DocumentType.DNI, "12345678"))).isEmpty();
    }

    @Test
    void a_number_without_a_type_is_flagged_on_the_type() {
        assertThat(fieldsInError(request(null, "12345678"))).contains("documentType");
    }

    @Test
    void a_type_without_a_number_is_flagged_on_the_document() {
        assertThat(fieldsInError(request(DocumentType.DNI, null))).contains("document");
    }

    @Test
    void a_dni_with_the_wrong_format_is_flagged_on_the_document() {
        // DNI exige 8 dígitos: "123" (corto) y "1234567X" (con letra) son inválidos.
        assertThat(fieldsInError(request(DocumentType.DNI, "123"))).contains("document");
        assertThat(fieldsInError(request(DocumentType.DNI, "1234567X"))).contains("document");
    }

    @Test
    void a_passport_is_alphanumeric() {
        assertThat(validator.validate(request(DocumentType.PASSPORT, "AB123456"))).isEmpty();
    }
}
