package com.odontorisas.dentalchart;

import com.jayway.jsonpath.JsonPath;
import com.odontorisas.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenarios del odontograma a nivel HTTP (contra PostgreSQL real, design.md D6/D10, `4.1`-`4.3`).
 * El sellado no tiene ruta propia acá — lo cubre {@code ClinicalHistoryControllerIT} vía
 * {@code /complete} (design.md D4): estos tests se quedan en {@code OPEN}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DentalChartControllerIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private String registerAdmin(String org, String email) throws Exception {
        String body = "{\"organizationName\":\"" + org + "\",\"email\":\"" + email
            + "\",\"password\":\"password123\",\"fullName\":\"Admin " + org + "\"}";
        MvcResult result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");
    }

    private long createPatient(String token, String fullName) throws Exception {
        String body = "{\"fullName\":\"" + fullName + "\",\"birthDate\":\"1990-01-01\",\"phone\":\"+51988877766\"}";
        MvcResult r = mockMvc.perform(post("/patients").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id")).longValue();
    }

    private void createBlock(String token, long patientId) throws Exception {
        mockMvc.perform(post("/patients/" + patientId + "/dental-chart")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"examinedOn\":\"2026-01-15\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void creates_the_initial_dental_chart_in_open() throws Exception {
        String token = registerAdmin("Clinica DC Create", "dc-create@clinica.test");
        long patientId = createPatient(token, "Ada Lovelace");

        mockMvc.perform(post("/patients/" + patientId + "/dental-chart")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"examinedOn\":\"2026-01-15\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.type").value("INITIAL"))
            .andExpect(jsonPath("$.patientId").value((int) patientId))
            .andExpect(jsonPath("$.findings").isArray())
            .andExpect(jsonPath("$.findings.length()").value(0));
    }

    @Test
    void rejects_a_second_initial_block_for_the_same_patient() throws Exception {
        String token = registerAdmin("Clinica DC Dup", "dc-dup@clinica.test");
        long patientId = createPatient(token, "Duplicado");
        createBlock(token, patientId);

        mockMvc.perform(post("/patients/" + patientId + "/dental-chart")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"examinedOn\":\"2026-01-15\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("/errors/dental-chart-block-already-exists"));
    }

    @Test
    void records_specifications_and_observations_as_separate_fields() throws Exception {
        String token = registerAdmin("Clinica DC Textos", "dc-textos@clinica.test");
        long patientId = createPatient(token, "Textos");
        createBlock(token, patientId);

        mockMvc.perform(put("/patients/" + patientId + "/dental-chart/specifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Movilidad grado M2\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.specifications").value("Movilidad grado M2"))
            .andExpect(jsonPath("$.specificationsReviewed").value(true))
            .andExpect(jsonPath("$.observationsReviewed").value(false));

        mockMvc.perform(put("/patients/" + patientId + "/dental-chart/observations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.observations").value(nullValue()))
            .andExpect(jsonPath("$.observationsReviewed").value(true));
    }

    @Test
    void adds_a_surface_finding_and_lists_it_on_the_block() throws Exception {
        String token = registerAdmin("Clinica DC Hallazgo", "dc-hallazgo@clinica.test");
        long patientId = createPatient(token, "Con Hallazgo");
        createBlock(token, patientId);

        String body = """
            {"type":"LESION_CARIES","subtype":"CE","location":{"locationKind":"SURFACE","toothA":36,"surface":"OCLUSAL"}}
            """;
        MvcResult added = mockMvc.perform(post("/patients/" + patientId + "/dental-chart/findings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.finding.type").value("LESION_CARIES"))
            .andExpect(jsonPath("$.finding.location.toothA").value(36))
            .andExpect(jsonPath("$.denticionWarning").value(false))
            .andReturn();
        long findingId = ((Number) JsonPath.read(
            added.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.finding.id")).longValue();

        mockMvc.perform(get("/patients/" + patientId + "/dental-chart").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.findings.length()").value(1))
            .andExpect(jsonPath("$.findings[0].id").value((int) findingId));
    }

    @Test
    void rejects_a_duplicate_finding_with_the_same_type_and_location() throws Exception {
        String token = registerAdmin("Clinica DC Hallazgo Dup", "dc-hallazgo-dup@clinica.test");
        long patientId = createPatient(token, "Hallazgo Duplicado");
        createBlock(token, patientId);
        String body = """
            {"type":"MOVILIDAD_PATOLOGICA","grade":2,"location":{"locationKind":"TOOTH","toothA":36}}
            """;
        mockMvc.perform(post("/patients/" + patientId + "/dental-chart/findings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/patients/" + patientId + "/dental-chart/findings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("/errors/dental-chart-finding-already-exists"));
    }

    @Test
    void rejects_a_finding_whose_location_does_not_match_the_type_scope() throws Exception {
        String token = registerAdmin("Clinica DC Incoherente", "dc-incoherente@clinica.test");
        long patientId = createPatient(token, "Incoherente");
        createBlock(token, patientId);

        // MOVILIDAD_PATOLOGICA es de alcance TOOTH, no SURFACE (@ValidFindingLocation).
        String body = """
            {"type":"MOVILIDAD_PATOLOGICA","grade":2,"location":{"locationKind":"SURFACE","toothA":36,"surface":"OCLUSAL"}}
            """;
        mockMvc.perform(post("/patients/" + patientId + "/dental-chart/findings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("/errors/validation-error"));
    }

    @Test
    void updates_and_then_removes_a_finding_while_the_block_is_open() throws Exception {
        String token = registerAdmin("Clinica DC Editar", "dc-editar@clinica.test");
        long patientId = createPatient(token, "Editar");
        createBlock(token, patientId);
        String create = """
            {"type":"LESION_CARIES","subtype":"MB","location":{"locationKind":"SURFACE","toothA":36,"surface":"OCLUSAL"}}
            """;
        MvcResult added = mockMvc.perform(post("/patients/" + patientId + "/dental-chart/findings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(create))
            .andExpect(status().isCreated())
            .andReturn();
        long findingId = ((Number) JsonPath.read(
            added.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.finding.id")).longValue();

        String update = """
            {"type":"LESION_CARIES","subtype":"CD","location":{"locationKind":"SURFACE","toothA":36,"surface":"OCLUSAL"}}
            """;
        mockMvc.perform(put("/patients/" + patientId + "/dental-chart/findings/" + findingId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(update))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.finding.subtype").value("CD"));

        mockMvc.perform(delete("/patients/" + patientId + "/dental-chart/findings/" + findingId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/patients/" + patientId + "/dental-chart").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.findings.length()").value(0));
    }

    @Test
    void a_clinic_cannot_read_another_clinics_dental_chart() throws Exception {
        String tokenA = registerAdmin("Clinica DC Aislada A", "dc-aislada-a@clinica.test");
        String tokenB = registerAdmin("Clinica DC Aislada B", "dc-aislada-b@clinica.test");
        long patientIdInA = createPatient(tokenA, "Paciente de A");
        createBlock(tokenA, patientIdInA);

        mockMvc.perform(get("/patients/" + patientIdInA + "/dental-chart").header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isNotFound());
    }

    @Test
    void the_catalog_carries_scope_color_behavior_and_acronym_for_every_type() throws Exception {
        String token = registerAdmin("Clinica DC Catalogo", "dc-catalogo@clinica.test");

        mockMvc.perform(get("/dental-chart/catalog").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThan(38)))
            .andExpect(jsonPath("$[?(@.type=='MOVILIDAD_PATOLOGICA')].requiresGrade", hasItem(true)))
            .andExpect(jsonPath("$[?(@.type=='CORONA' && @.subtype=='CM')].scope", hasItem("TOOTH")));
    }

    // --- scenarios que la revisión externa encontró sin test explícito ---

    private void expectRejected(String token, long patientId, String body) throws Exception {
        mockMvc.perform(post("/patients/" + patientId + "/dental-chart/findings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/patients/" + patientId + "/dental-chart").header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.findings.length()").value(0));
    }

    @Test
    void rejects_a_condition_type_outside_the_catalog() throws Exception {
        String token = registerAdmin("Clinica DC Fuera", "dc-fuera@clinica.test");
        long patientId = createPatient(token, "Fuera de catalogo");
        createBlock(token, patientId);

        expectRejected(token, patientId, """
            {"type":"RECESION_GINGIVAL","location":{"locationKind":"TOOTH","toothA":36}}
            """);
    }

    @Test
    void rejects_an_arch_that_is_neither_upper_nor_lower() throws Exception {
        String token = registerAdmin("Clinica DC Arco", "dc-arco@clinica.test");
        long patientId = createPatient(token, "Arco invalido");
        createBlock(token, patientId);

        expectRejected(token, patientId, """
            {"type":"EDENTULO_TOTAL","location":{"locationKind":"ARCH","arch":"LATERAL"}}
            """);
    }

    @Test
    void rejects_range_teeth_on_a_finding_that_is_not_a_range() throws Exception {
        String token = registerAdmin("Clinica DC Piezas", "dc-piezas@clinica.test");
        long patientId = createPatient(token, "Piezas sueltas");
        createBlock(token, patientId);

        mockMvc.perform(post("/patients/" + patientId + "/dental-chart/findings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"type":"GIROVERSION","location":{"locationKind":"TOOTH","toothA":21,"teeth":[21,22]}}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("/errors/validation-error"))
            .andExpect(jsonPath("$.errors[*].field", hasItem("location.teeth")));
    }

    /** Spec "Registrar un hallazgo con su subtipo": la sigla oficial se expone por el catálogo
     *  (tipo + subtipo es la clave; la sigla es atributo de salida, ADR-0017 D7 — no es única). */
    @Test
    void a_finding_with_subtype_exposes_its_official_acronym_through_the_catalog() throws Exception {
        String token = registerAdmin("Clinica DC Sigla", "dc-sigla@clinica.test");
        long patientId = createPatient(token, "Sigla");
        createBlock(token, patientId);

        mockMvc.perform(post("/patients/" + patientId + "/dental-chart/findings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"type":"LESION_CARIES","subtype":"CD","location":{"locationKind":"SURFACE","toothA":36,"surface":"OCLUSAL"}}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.finding.type").value("LESION_CARIES"))
            .andExpect(jsonPath("$.finding.subtype").value("CD"));

        mockMvc.perform(get("/dental-chart/catalog").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.type=='LESION_CARIES' && @.subtype=='CD')].acronym", hasItem("CD")));
    }
}