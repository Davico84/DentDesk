package com.odontorisas.consultationnote;

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
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La nota de consulta y el congelamiento de la anamnesis a nivel HTTP, contra PostgreSQL real:
 * status reales, {@code 404} para otra clínica, {@code 409} sobre una nota sellada aun por API directa
 * (scenario <i>Rechazar cambios a una nota sellada aun por API directa</i>), y los rechazos que hace
 * Bean Validation antes del servicio (tipo de diagnóstico ausente, corrección de un diagnóstico,
 * alta sin categoría).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsultationNoteControllerIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    DentalChartService dentalChartService;

    private static final String FULL_HC = """
        {"motivoConsulta":"Dolor de muela","enfermedadActual":"3 días","antecedentesPersonales":"Ninguno",
         "examenClinico":"Normal","pronostico":"Favorable","alergiasRevisado":true,"medicacionRevisado":true,
         "antecedentesFamiliaresRevisado":true}
        """;
    private static final String A00 = "{\"cie10Code\":\"A00\",\"ubicacion\":\"GENERAL\",\"tipoDiagnostico\":\"DEFINITIVO\"}";

    private String token;

    private String registerAdmin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String body = invitedAdminRegisterBody(
            "Clinica Notas " + suffix, "cn-" + suffix + "@clinica.test", "password123", "Admin");
        MvcResult r = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                               String jsonBody) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (jsonBody != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(jsonBody);
        }
        return mockMvc.perform(req);
    }

    private long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
            "$.id")).longValue();
    }

    private long createPatient() throws Exception {
        return idOf(call(post("/patients"), "{\"fullName\":\"Paciente\",\"birthDate\":\"1990-01-01\","
            + "\"phone\":\"+51988877766\",\"allergies\":[\"Látex\"]}").andExpect(status().isCreated()));
    }

    /** Paciente con la admisión cerrada por la ruta HTTP real; el odontograma se siembra por servicio. */
    private long patientWithSealedHistory() throws Exception {
        long patientId = createPatient();
        String base = "/patients/" + patientId + "/clinical-history";
        call(post(base), null).andExpect(status().isCreated());
        call(put(base), FULL_HC).andExpect(status().isOk());
        call(post(base + "/diagnoses"), A00).andExpect(status().isCreated());
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
        call(post(base + "/complete"), null).andExpect(status().isOk());
        return patientId;
    }

    private static String attendedAt(long hoursAgo) {
        return Instant.now().minus(hoursAgo, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private long sealedNote(long patientId) throws Exception {
        String notes = "/patients/" + patientId + "/consultation-notes";
        String when = attendedAt(1);
        long noteId = idOf(call(post(notes), "{\"attendedAt\":\"" + when + "\"}").andExpect(status().isCreated()));
        call(put(notes + "/" + noteId), "{\"attendedAt\":\"" + when + "\",\"reason\":\"Control\","
            + "\"examination\":\"Encía sana\",\"treatment\":\"Profilaxis\"}").andExpect(status().isOk());
        call(post(notes + "/" + noteId + "/diagnoses"), A00).andExpect(status().isCreated());
        call(post(notes + "/" + noteId + "/seal"), null).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SELLADA"));
        return noteId;
    }

    @Test
    void the_full_cycle_open_save_diagnose_seal_and_correct() throws Exception {
        token = registerAdmin();
        long patientId = patientWithSealedHistory();
        String notes = "/patients/" + patientId + "/consultation-notes";

        long noteId = sealedNote(patientId);

        call(post(notes + "/" + noteId + "/corrections"),
            "{\"field\":\"EXAMINATION\",\"correctedText\":\"Encía sana; caries 37\",\"reason\":\"Faltó la pieza\"}")
            .andExpect(status().isCreated());
        call(get(notes + "/" + noteId), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.examination").value("Encía sana"))
            .andExpect(jsonPath("$.corrections[0].correctedText").value("Encía sana; caries 37"))
            .andExpect(jsonPath("$.diagnoses[0].cie10Code").value("A00"));
        call(get(notes), null).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value((int) noteId));
    }

    @Test
    void a_sealed_note_rejects_every_write_sent_straight_to_the_api() throws Exception {
        token = registerAdmin();
        long patientId = patientWithSealedHistory();
        String note = "/patients/" + patientId + "/consultation-notes/" + sealedNote(patientId);

        call(put(note), "{\"attendedAt\":\"" + attendedAt(1) + "\",\"examination\":\"otro\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("/errors/consultation-note-sealed"));
        call(post(note + "/diagnoses"), A00).andExpect(status().isConflict());
        call(post(note + "/background-entries"), "{\"kind\":\"ALTA\",\"category\":\"MEDICACION\",\"description\":\"X\"}")
            .andExpect(status().isConflict());
        call(post(note + "/findings"), "{\"findingId\":1,\"description\":\"Pieza 16: caries\"}")
            .andExpect(status().isConflict());
        call(delete(note), null).andExpect(status().isConflict());
        call(post(note + "/seal"), null).andExpect(status().isConflict());
    }

    @Test
    void attended_findings_are_validated_over_http_and_listed_in_the_note() throws Exception {
        token = registerAdmin();
        long patientId = patientWithSealedHistory();
        String notes = "/patients/" + patientId + "/consultation-notes";
        long noteId = idOf(call(post(notes), "{\"attendedAt\":\"" + attendedAt(1) + "\"}").andExpect(status().isCreated()));
        String note = notes + "/" + noteId;

        call(get(note), null).andExpect(status().isOk()).andExpect(jsonPath("$.attendedFindings").isArray());
        call(post(note + "/findings"), "{\"description\":\"Pieza 16\"}").andExpect(status().isBadRequest());
        call(post(note + "/findings"), "{\"findingId\":1,\"description\":\"   \"}").andExpect(status().isBadRequest());
        call(post(note + "/findings"), "{\"findingId\":1,\"description\":\"" + "x".repeat(301) + "\"}")
            .andExpect(status().isBadRequest());
        call(post(note + "/findings"), "{\"findingId\":999999,\"description\":\"Pieza 16\"}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("/errors/consultation-note-finding-not-found"));
    }

    @Test
    void cie10_suggestions_are_served_for_a_finding_type() throws Exception {
        token = registerAdmin();
        call(get("/cie10/suggestions").param("findingType", "LESION_CARIES").param("subtype", "CD"), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("K021"));
        call(get("/cie10/suggestions").param("findingType", "CORONA"), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void opening_without_a_sealed_history_is_a_409_and_sealing_incomplete_lists_missing_fields() throws Exception {
        token = registerAdmin();
        long withoutHistory = createPatient();
        call(post("/patients/" + withoutHistory + "/consultation-notes"), "{\"attendedAt\":\"" + attendedAt(1) + "\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("/errors/clinical-history-not-completed"));

        long patientId = patientWithSealedHistory();
        String notes = "/patients/" + patientId + "/consultation-notes";
        long noteId = idOf(call(post(notes), "{\"attendedAt\":\"" + attendedAt(1) + "\"}"));
        call(post(notes + "/" + noteId + "/seal"), null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.missingFields", contains("reason", "examination", "treatment", "diagnoses")));
    }

    @Test
    void a_note_of_another_clinic_is_a_404() throws Exception {
        token = registerAdmin();
        long patientId = patientWithSealedHistory();
        long noteId = sealedNote(patientId);

        token = registerAdmin();
        call(get("/patients/" + patientId + "/consultation-notes/" + noteId), null).andExpect(status().isNotFound());
        call(post("/patients/" + patientId + "/consultation-notes/" + noteId + "/corrections"),
            "{\"field\":\"REASON\",\"correctedText\":\"x\",\"reason\":\"y\"}").andExpect(status().isNotFound());
    }

    @Test
    void bean_validation_rejects_malformed_requests_before_the_service() throws Exception {
        token = registerAdmin();
        long patientId = patientWithSealedHistory();
        String notes = "/patients/" + patientId + "/consultation-notes";
        long sealed = sealedNote(patientId);
        long open = idOf(call(post(notes), "{\"attendedAt\":\"" + attendedAt(1) + "\"}"));

        // Diagnóstico sin tipo P/D/R.
        call(post(notes + "/" + open + "/diagnoses"), "{\"cie10Code\":\"A00\",\"ubicacion\":\"GENERAL\"}")
            .andExpect(status().isBadRequest());
        // Corrección de un diagnóstico: no es un campo corregible (art. 47 c).
        call(post(notes + "/" + sealed + "/corrections"),
            "{\"field\":\"DIAGNOSES\",\"correctedText\":\"K02\",\"reason\":\"error\"}")
            .andExpect(status().isBadRequest());
        // Corrección sin motivo.
        call(post(notes + "/" + sealed + "/corrections"), "{\"field\":\"REASON\",\"correctedText\":\"x\"}")
            .andExpect(status().isBadRequest());
        // Alta sin categoría, sin descripción o con descripción en blanco.
        call(post(notes + "/" + open + "/background-entries"), "{\"kind\":\"ALTA\",\"description\":\"Látex\"}")
            .andExpect(status().isBadRequest());
        call(post(notes + "/" + open + "/background-entries"), "{\"kind\":\"ALTA\",\"category\":\"MEDICACION\"}")
            .andExpect(status().isBadRequest());
        call(post(notes + "/" + open + "/background-entries"),
            "{\"kind\":\"ALTA\",\"category\":\"MEDICACION\",\"description\":\"  \"}")
            .andExpect(status().isBadRequest());
        // Categoría fuera de las tres.
        call(post(notes + "/" + open + "/background-entries"),
            "{\"kind\":\"ALTA\",\"category\":\"HABITOS\",\"description\":\"Fuma\"}")
            .andExpect(status().isBadRequest());
        // Apertura sin fecha de atención.
        call(post("/patients/" + createPatient() + "/consultation-notes"), "{}").andExpect(status().isBadRequest());
    }

    @Test
    void the_active_background_lists_each_item_with_its_source() throws Exception {
        token = registerAdmin();
        long patientId = patientWithSealedHistory();

        call(get("/patients/" + patientId + "/clinical-background"), null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.source == 'FICHA')].description", contains("Látex")))
            .andExpect(jsonPath("$.items[?(@.source == 'HISTORIA')].description", contains("Ninguno")));
    }

    // --- patients: La anamnesis de la ficha se congela al existir la Historia Clínica ---

    private static String patientBody(String allergy, String phone) {
        return "{\"fullName\":\"Paciente\",\"birthDate\":\"1990-01-01\",\"phone\":\"" + phone + "\","
            + "\"allergies\":[\"" + allergy + "\"]}";
    }

    @Test
    void the_anamnesis_is_editable_while_there_is_no_clinical_history() throws Exception {
        token = registerAdmin();
        long patientId = createPatient();

        call(put("/patients/" + patientId), patientBody("Penicilina", "+51988877766"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allergies[0]").value("Penicilina"))
            .andExpect(jsonPath("$.anamnesisFrozen").value(false));
    }

    @Test
    void changing_the_anamnesis_once_there_is_a_clinical_history_is_rejected_and_nothing_changes() throws Exception {
        token = registerAdmin();
        long patientId = createPatient();
        call(post("/patients/" + patientId + "/clinical-history"), null).andExpect(status().isCreated());

        call(put("/patients/" + patientId), patientBody("Penicilina", "+51911122233"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("/errors/patient-anamnesis-frozen"));
        call(get("/patients/" + patientId), null)
            .andExpect(jsonPath("$.allergies[0]").value("Látex"))
            .andExpect(jsonPath("$.phone").value("+51988877766"));
    }

    @Test
    void the_rest_of_the_record_stays_editable_and_the_frozen_anamnesis_stays_readable() throws Exception {
        token = registerAdmin();
        long patientId = createPatient();
        call(post("/patients/" + patientId + "/clinical-history"), null).andExpect(status().isCreated());

        call(put("/patients/" + patientId), patientBody("Látex", "+51911122233"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phone").value("+51911122233"));
        call(get("/patients/" + patientId), null)
            .andExpect(jsonPath("$.allergies[0]").value("Látex"))
            .andExpect(jsonPath("$.anamnesisFrozen").value(true));
    }
}
