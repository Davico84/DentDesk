package com.odontorisas.patient;

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
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenarios de {@code patients} a nivel HTTP (contra PostgreSQL real). El aislamiento que aquí se
 * comprueba lo da el filtro de aplicación ({@code @TenantId}): la clínica A recibe <b>404</b> —no
 * 403— al pedir un paciente de B por su id, sin revelar que existe en otra clínica. Es
 * complementario al de motor ({@code PatientRlsIT}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PatientControllerIT extends AbstractIntegrationTest {

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

    private String adultBody(String fullName, String document) {
        return "{\"fullName\":\"" + fullName + "\",\"birthDate\":\"1990-01-01\""
            + (document == null ? "" : ",\"document\":\"" + document + "\"") + "}";
    }

    private long createPatient(String token, String body) throws Exception {
        MvcResult r = mockMvc.perform(post("/patients").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id")).longValue();
    }

    @Test
    void create_and_read_back_a_patient() throws Exception {
        String token = registerAdmin("Clinica Alta", "patients-create@clinica.test");
        long id = createPatient(token, adultBody("Ada Lovelace", "DOC-1"));

        mockMvc.perform(get("/patients/" + id).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Ada Lovelace"))
            .andExpect(jsonPath("$.minor").value(false));
    }

    @Test
    void a_minor_without_guardian_is_rejected_with_field_errors() throws Exception {
        String token = registerAdmin("Clinica Menor", "patients-minor@clinica.test");
        String minor = "{\"fullName\":\"Niño\",\"birthDate\":\"" + LocalDate.now().minusYears(8) + "\"}";

        mockMvc.perform(post("/patients").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(minor))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItem("guardianName")));
    }

    @Test
    void a_duplicate_document_in_the_same_clinic_is_a_conflict() throws Exception {
        String token = registerAdmin("Clinica Dup", "patients-dup@clinica.test");
        createPatient(token, adultBody("Primero", "SAME"));

        mockMvc.perform(post("/patients").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(adultBody("Segundo", "SAME")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("/errors/patient-document-taken"));
    }

    @Test
    void a_clinic_cannot_read_a_patient_of_another_clinic_even_by_id() throws Exception {
        String tokenA = registerAdmin("Clinica A HTTP", "patients-a@clinica.test");
        String tokenB = registerAdmin("Clinica B HTTP", "patients-b@clinica.test");
        long idInA = createPatient(tokenA, adultBody("Paciente de A", "ONLY-A"));

        // B pide el paciente de A por su id EXACTO → 404, sin revelar que existe en otra clínica.
        mockMvc.perform(get("/patients/" + idInA).header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("/errors/patient-not-found"));
    }

    @Test
    void the_same_document_is_allowed_in_a_different_clinic() throws Exception {
        String tokenA = registerAdmin("Clinica Doc A", "patients-doc-a@clinica.test");
        String tokenB = registerAdmin("Clinica Doc B", "patients-doc-b@clinica.test");
        createPatient(tokenA, adultBody("Persona en A", "SHARED"));

        // Misma persona, otra clínica: mismo documento, sin conflicto.
        createPatient(tokenB, adultBody("Persona en B", "SHARED"));
    }

    @Test
    void the_list_only_shows_patients_of_the_active_clinic() throws Exception {
        String tokenA = registerAdmin("Clinica List A", "patients-list-a@clinica.test");
        String tokenB = registerAdmin("Clinica List B", "patients-list-b@clinica.test");
        createPatient(tokenA, adultBody("Solo en A", "LIST-A"));
        createPatient(tokenB, adultBody("Solo en B", "LIST-B"));

        mockMvc.perform(get("/patients").header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].fullName").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Solo en A"))))
            .andExpect(jsonPath("$.content[*].fullName").value(org.hamcrest.Matchers.hasItem("Solo en B")));
    }
}
