package com.odontorisas.clinicalhistory;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.jayway.jsonpath.JsonPath;
import com.odontorisas.AbstractIntegrationTest;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.service.dentalchart.DentalChartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenarios de {@code clinical-history} a nivel HTTP (contra PostgreSQL real). Cubre el ciclo
 * completo: crear, guardar incremental, agregar diagnóstico, sellar, y el gate de habilitación.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClinicalHistoryControllerIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DentalChartService dentalChartService;

    /**
     * Siembra un odontograma inicial listo para sellar (design.md D4, `add-dental-chart`): desde
     * este change, {@code /complete} sella <b>ambos</b> documentos, y el odontograma no tiene
     * endpoint HTTP todavía (llega en el grupo 4) — se siembra por el servicio directo, con el
     * contexto de tenant que normalmente pone el filtro JWT en una petición real. {@code oid} y
     * {@code sub} son los claims que {@code TokenService} escribe en el token.
     */
    private void givenAReadyDentalChart(String token, long patientId) {
        DecodedJWT jwt = JWT.decode(token);
        IdentityContext.set(Long.valueOf(jwt.getSubject()));
        OrganizationContext.set(jwt.getClaim("oid").asLong());
        try {
            dentalChartService.createInitial(patientId, LocalDate.now());
            dentalChartService.setSpecifications(patientId, null);
            dentalChartService.setObservations(patientId, null);
        } finally {
            IdentityContext.clear();
            OrganizationContext.clear();
        }
    }

    private String registerAdmin(String org, String email) throws Exception {
        String body = invitedAdminRegisterBody(org, email, "password123", "Admin " + org);
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

    private static final String FULL_UPDATE_BODY = """
        {"motivoConsulta":"Dolor de muela","enfermedadActual":"Desde hace 3 días",
         "antecedentesPersonales":"Ninguno relevante","examenClinico":"Sin hallazgos anormales",
         "pronostico":"Favorable","alergiasRevisado":true,"medicacionRevisado":true,
         "antecedentesFamiliaresRevisado":true}
        """;

    @Test
    void creates_a_clinical_history_in_draft() throws Exception {
        String token = registerAdmin("Clinica CH Create", "ch-create@clinica.test");
        long patientId = createPatient(token, "Ada Lovelace");

        mockMvc.perform(post("/patients/" + patientId + "/clinical-history")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.patientId").value((int) patientId))
            .andExpect(jsonPath("$.fullName").value("Ada Lovelace"));
    }

    @Test
    void rejects_a_second_clinical_history_for_the_same_patient() throws Exception {
        String token = registerAdmin("Clinica CH Dup", "ch-dup@clinica.test");
        long patientId = createPatient(token, "Duplicado");
        mockMvc.perform(post("/patients/" + patientId + "/clinical-history").header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/patients/" + patientId + "/clinical-history").header("Authorization", "Bearer " + token))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("/errors/clinical-history-already-exists"));
    }

    @Test
    void saves_incremental_draft_and_reflects_current_patient_filiation() throws Exception {
        String token = registerAdmin("Clinica CH Filiacion", "ch-filiacion@clinica.test");
        long patientId = createPatient(token, "Nombre Original");
        mockMvc.perform(post("/patients/" + patientId + "/clinical-history").header("Authorization", "Bearer " + token));

        mockMvc.perform(put("/patients/" + patientId + "/clinical-history")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivoConsulta\":\"Dolor\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.motivoConsulta").value("Dolor"))
            .andExpect(jsonPath("$.status").value("DRAFT"));

        // Cambia el nombre del paciente DESPUÉS de guardar la historia: la filiación mostrada
        // debe reflejar el dato vigente (design.md D7), no un snapshot.
        mockMvc.perform(put("/patients/" + patientId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Nombre Actualizado\",\"birthDate\":\"1990-01-01\",\"phone\":\"+51988877766\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/patients/" + patientId + "/clinical-history").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Nombre Actualizado"));
    }

    @Test
    void completes_the_history_and_reports_it_as_enabled_for_treatment() throws Exception {
        String token = registerAdmin("Clinica CH Complete", "ch-complete@clinica.test");
        long patientId = createPatient(token, "Completa");
        mockMvc.perform(post("/patients/" + patientId + "/clinical-history").header("Authorization", "Bearer " + token));

        mockMvc.perform(get("/patients/" + patientId + "/clinical-history/enablement")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.enabledForTreatment").value(false));

        mockMvc.perform(put("/patients/" + patientId + "/clinical-history")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(FULL_UPDATE_BODY))
            .andExpect(status().isOk());

        mockMvc.perform(post("/patients/" + patientId + "/clinical-history/diagnoses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cie10Code\":\"A00\",\"ubicacion\":\"GENERAL\",\"tipoDiagnostico\":\"PRESUNTIVO\"}"))
            .andExpect(status().isCreated());
        givenAReadyDentalChart(token, patientId);

        mockMvc.perform(post("/patients/" + patientId + "/clinical-history/complete")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/patients/" + patientId + "/clinical-history/enablement")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.enabledForTreatment").value(true));
    }

    @Test
    void rejects_completion_with_missing_checklist_items() throws Exception {
        String token = registerAdmin("Clinica CH Checklist", "ch-checklist@clinica.test");
        long patientId = createPatient(token, "Incompleta");
        mockMvc.perform(post("/patients/" + patientId + "/clinical-history").header("Authorization", "Bearer " + token));

        mockMvc.perform(post("/patients/" + patientId + "/clinical-history/complete")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("/errors/clinical-history-incomplete"))
            .andExpect(jsonPath("$.missingFields").isArray())
            .andExpect(jsonPath("$.missingFields", org.hamcrest.Matchers.hasItem("motivoConsulta")));
    }

    @Test
    void rejects_editing_a_completed_history_even_by_direct_api_call() throws Exception {
        String token = registerAdmin("Clinica CH Sellada", "ch-sellada@clinica.test");
        long patientId = createPatient(token, "Sellada");
        mockMvc.perform(post("/patients/" + patientId + "/clinical-history").header("Authorization", "Bearer " + token));
        mockMvc.perform(put("/patients/" + patientId + "/clinical-history")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(FULL_UPDATE_BODY));
        mockMvc.perform(post("/patients/" + patientId + "/clinical-history/diagnoses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cie10Code\":\"A00\",\"ubicacion\":\"GENERAL\",\"tipoDiagnostico\":\"PRESUNTIVO\"}"));
        givenAReadyDentalChart(token, patientId);
        mockMvc.perform(post("/patients/" + patientId + "/clinical-history/complete")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(put("/patients/" + patientId + "/clinical-history")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivoConsulta\":\"Intento de edición\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("/errors/clinical-history-sealed"));
    }

    @Test
    void rejects_a_diagnosis_with_a_nonexistent_cie10_code() throws Exception {
        String token = registerAdmin("Clinica CH Cie10", "ch-cie10@clinica.test");
        long patientId = createPatient(token, "Diag Invalido");
        mockMvc.perform(post("/patients/" + patientId + "/clinical-history").header("Authorization", "Bearer " + token));

        mockMvc.perform(post("/patients/" + patientId + "/clinical-history/diagnoses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cie10Code\":\"ZZZZ\",\"ubicacion\":\"GENERAL\",\"tipoDiagnostico\":\"PRESUNTIVO\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("/errors/cie10-code-not-found"));
    }

    @Test
    void removes_a_diagnosis_while_in_draft() throws Exception {
        String token = registerAdmin("Clinica CH Remove", "ch-remove@clinica.test");
        long patientId = createPatient(token, "Elimina Diag");
        mockMvc.perform(post("/patients/" + patientId + "/clinical-history").header("Authorization", "Bearer " + token));
        MvcResult added = mockMvc.perform(post("/patients/" + patientId + "/clinical-history/diagnoses")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cie10Code\":\"A00\",\"ubicacion\":\"GENERAL\",\"tipoDiagnostico\":\"PRESUNTIVO\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        long diagnosisId = ((Number) JsonPath.read(added.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id")).longValue();

        mockMvc.perform(delete("/patients/" + patientId + "/clinical-history/diagnoses/" + diagnosisId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/patients/" + patientId + "/clinical-history").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.diagnosticos").isArray())
            .andExpect(jsonPath("$.diagnosticos.length()").value(0));
    }

    @Test
    void a_clinic_cannot_read_another_clinics_clinical_history() throws Exception {
        String tokenA = registerAdmin("Clinica CH Aislada A", "ch-aislada-a@clinica.test");
        String tokenB = registerAdmin("Clinica CH Aislada B", "ch-aislada-b@clinica.test");
        long patientIdInA = createPatient(tokenA, "Paciente de A");
        mockMvc.perform(post("/patients/" + patientIdInA + "/clinical-history").header("Authorization", "Bearer " + tokenA));

        mockMvc.perform(get("/patients/" + patientIdInA + "/clinical-history").header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isNotFound());
    }
}
