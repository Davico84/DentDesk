package com.odontorisas.dentalchart;

import com.odontorisas.common.DentalArch;
import com.odontorisas.common.FindingCondition;
import com.odontorisas.common.FindingType;
import com.odontorisas.common.LocationKind;
import com.odontorisas.common.ToothSurface;
import com.odontorisas.presentation.dto.DentalChartFindingRequest;
import com.odontorisas.presentation.dto.DentalChartLocationRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las seis invariantes cruzadas de {@code @ValidFindingLocation} (design.md D9). Mismo estilo que
 * {@code ValidDiagnosisLocationValidatorTest}: {@link Validator} real, sin Spring ni base de
 * datos.
 */
class ValidFindingLocationValidatorTest {

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

    private Set<String> fieldsInError(DentalChartFindingRequest request) {
        Set<ConstraintViolation<DentalChartFindingRequest>> violations = validator.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private static DentalChartLocationRequest surface(Integer tooth, ToothSurface s) {
        return new DentalChartLocationRequest(LocationKind.SURFACE, tooth, null, s, null, null);
    }

    private static DentalChartLocationRequest tooth(Integer t) {
        return new DentalChartLocationRequest(LocationKind.TOOTH, t, null, null, null, null);
    }

    private static DentalChartLocationRequest between(Integer a, Integer b) {
        return new DentalChartLocationRequest(LocationKind.BETWEEN_TEETH, a, b, null, null, null);
    }

    private static DentalChartLocationRequest range(List<Integer> teeth) {
        return new DentalChartLocationRequest(LocationKind.TOOTH_RANGE, null, null, null, null, teeth);
    }

    private static DentalChartLocationRequest arch(DentalArch a) {
        return new DentalChartLocationRequest(LocationKind.ARCH, null, null, null, a, null);
    }

    // --- casos aceptados, uno por alcance ---

    @Test
    void a_surface_finding_with_an_applicable_surface_is_accepted() {
        var request = new DentalChartFindingRequest(FindingType.LESION_CARIES, "MB", null, null,
            surface(36, ToothSurface.OCLUSAL));
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void a_tooth_finding_is_accepted() {
        var request = new DentalChartFindingRequest(FindingType.GIROVERSION, null, null, null, tooth(21));
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void a_between_teeth_finding_is_accepted() {
        var request = new DentalChartFindingRequest(FindingType.DIASTEMA, null, null, null, between(11, 21));
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void a_tooth_range_finding_with_two_teeth_is_accepted() {
        var request = new DentalChartFindingRequest(FindingType.PROTESIS_PARCIAL_FIJA, null,
            FindingCondition.GOOD, null, range(List.of(34, 36)));
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void an_arch_finding_is_accepted() {
        var request = new DentalChartFindingRequest(FindingType.EDENTULO_TOTAL, null, null, null,
            arch(DentalArch.SUPERIOR));
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void a_finding_with_subtype_and_the_official_acronym_is_accepted() {
        var request = new DentalChartFindingRequest(FindingType.CORONA, "CM", FindingCondition.GOOD, null,
            tooth(16));
        assertThat(validator.validate(request)).isEmpty();
    }

    // --- 1. coherencia alcance <-> forma de localización ---

    @Test
    void a_tooth_scoped_type_with_a_surface_location_is_rejected() {
        var request = new DentalChartFindingRequest(FindingType.GIROVERSION, null, null, null,
            surface(21, ToothSurface.VESTIBULAR));
        assertThat(fieldsInError(request)).contains("location.locationKind");
    }

    @Test
    void a_missing_location_kind_is_rejected() {
        var request = new DentalChartFindingRequest(FindingType.GIROVERSION, null, null, null,
            new DentalChartLocationRequest(null, 21, null, null, null, null));
        assertThat(fieldsInError(request)).contains("location.locationKind");
    }

    // --- 2. FDI real y superficie aplicable a la pieza ---

    @Test
    void an_invalid_fdi_number_is_rejected() {
        var request = new DentalChartFindingRequest(FindingType.GIROVERSION, null, null, null, tooth(99));
        assertThat(fieldsInError(request)).contains("location.toothA");
    }

    @Test
    void an_oclusal_surface_on_an_anterior_tooth_is_rejected() {
        // 11 es incisivo central superior: anterior, no admite OCLUSAL (admite INCISAL).
        var request = new DentalChartFindingRequest(FindingType.LESION_CARIES, "MB", null, null,
            surface(11, ToothSurface.OCLUSAL));
        assertThat(fieldsInError(request)).contains("location.surface");
    }

    @Test
    void an_incisal_surface_on_a_posterior_tooth_is_rejected() {
        // 36 es molar: posterior, no admite INCISAL (admite OCLUSAL).
        var request = new DentalChartFindingRequest(FindingType.LESION_CARIES, "MB", null, null,
            surface(36, ToothSurface.INCISAL));
        assertThat(fieldsInError(request)).contains("location.surface");
    }

    // --- 3. rango con más de una pieza ---

    @Test
    void a_range_with_a_single_tooth_is_rejected() {
        var request = new DentalChartFindingRequest(FindingType.PROTESIS_PARCIAL_FIJA, null,
            FindingCondition.GOOD, null, range(List.of(34)));
        assertThat(fieldsInError(request)).contains("location.teeth");
    }

    @Test
    void a_range_with_no_teeth_is_rejected() {
        var request = new DentalChartFindingRequest(FindingType.PROTESIS_PARCIAL_FIJA, null,
            FindingCondition.GOOD, null, range(List.of()));
        assertThat(fieldsInError(request)).contains("location.teeth");
    }

    @Test
    void a_range_with_a_repeated_tooth_is_rejected() {
        var request = new DentalChartFindingRequest(FindingType.PROTESIS_PARCIAL_FIJA, null,
            FindingCondition.GOOD, null, range(List.of(34, 34, 36)));
        assertThat(fieldsInError(request)).contains("location.teeth");
    }

    // --- 4. grado exigido/prohibido según el tipo ---

    @Test
    void mobility_without_grade_is_rejected() {
        var request = new DentalChartFindingRequest(FindingType.MOVILIDAD_PATOLOGICA, null, null, null, tooth(21));
        assertThat(fieldsInError(request)).contains("grade");
    }

    @Test
    void mobility_with_a_grade_outside_1_to_3_is_rejected() {
        var request = new DentalChartFindingRequest(FindingType.MOVILIDAD_PATOLOGICA, null, null, (short) 4,
            tooth(21));
        assertThat(fieldsInError(request)).contains("grade");
    }

    @Test
    void a_non_mobility_type_with_a_grade_is_rejected() {
        var request = new DentalChartFindingRequest(FindingType.GIROVERSION, null, null, (short) 1, tooth(21));
        assertThat(fieldsInError(request)).contains("grade");
    }

    // --- 5. subtipo exigido/prohibido y perteneciente al tipo ---

    @Test
    void a_type_with_subtypes_without_a_subtype_is_rejected() {
        var request = new DentalChartFindingRequest(FindingType.CORONA, null, FindingCondition.GOOD, null,
            tooth(16));
        assertThat(fieldsInError(request)).contains("subtype");
    }

    @Test
    void a_subtype_not_belonging_to_the_type_is_rejected() {
        // "MB" es subtipo de LESION_CARIES, no de CORONA.
        var request = new DentalChartFindingRequest(FindingType.CORONA, "MB", FindingCondition.GOOD, null,
            tooth(16));
        assertThat(fieldsInError(request)).contains("subtype");
    }

    @Test
    void a_type_without_subtypes_with_a_subtype_is_rejected() {
        var request = new DentalChartFindingRequest(FindingType.GIROVERSION, "X", null, null, tooth(21));
        assertThat(fieldsInError(request)).contains("subtype");
    }

    // --- 6. estado de conservación exigido solo en SEGUN_ESTADO ---

    @Test
    void a_segun_estado_type_without_condition_is_rejected() {
        var request = new DentalChartFindingRequest(FindingType.CORONA, "CM", null, null, tooth(16));
        assertThat(fieldsInError(request)).contains("condition");
    }

    @Test
    void a_siempre_azul_type_with_condition_is_rejected() {
        var request = new DentalChartFindingRequest(FindingType.GIROVERSION, null, FindingCondition.GOOD, null,
            tooth(21));
        assertThat(fieldsInError(request)).contains("condition");
    }

    @Test
    void a_siempre_rojo_type_with_condition_is_rejected() {
        var request = new DentalChartFindingRequest(FindingType.MOVILIDAD_PATOLOGICA, null, FindingCondition.POOR,
            (short) 2, tooth(21));
        assertThat(fieldsInError(request)).contains("condition");
    }

    // --- coexistencia normativa: sellante y caries en la misma pieza, cada uno su propio hallazgo ---

    @Test
    void a_sellant_and_a_caries_lesion_on_the_same_surface_are_each_independently_valid() {
        var sellant = new DentalChartFindingRequest(FindingType.SELLANTE, null, FindingCondition.GOOD, null,
            surface(16, ToothSurface.OCLUSAL));
        var caries = new DentalChartFindingRequest(FindingType.LESION_CARIES, "MB", null, null,
            surface(16, ToothSurface.OCLUSAL));
        assertThat(validator.validate(sellant)).isEmpty();
        assertThat(validator.validate(caries)).isEmpty();
    }

    // --- implante coexiste con la ausencia: ambos son TOOTH, ninguno choca en el boundary ---

    @Test
    void an_implant_and_an_absent_tooth_on_the_same_piece_are_each_independently_valid() {
        var absent = new DentalChartFindingRequest(FindingType.PIEZA_AUSENTE, "DEX", null, null, tooth(36));
        var implant = new DentalChartFindingRequest(FindingType.IMPLANTE_DENTAL, null, FindingCondition.GOOD, null,
            tooth(36));
        assertThat(validator.validate(absent)).isEmpty();
        assertThat(validator.validate(implant)).isEmpty();
    }
}
