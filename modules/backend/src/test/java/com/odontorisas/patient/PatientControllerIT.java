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

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        // Si hay documento, va emparejado con su tipo (DNI = 8 dígitos), como exige @ValidDocument.
        // Con teléfono propio: así el contacto de emergencia (update-patients D2) no interfiere en
        // tests que no lo están probando.
        return "{\"fullName\":\"" + fullName + "\",\"birthDate\":\"1990-01-01\",\"phone\":\"+51988877766\""
            + (document == null ? "" : ",\"documentType\":\"DNI\",\"document\":\"" + document + "\"") + "}";
    }

    private long createPatient(String token, String body) throws Exception {
        MvcResult r = mockMvc.perform(post("/patients").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id")).longValue();
    }

    private String bodyWithPhone(String fullName, String phone) {
        return "{\"fullName\":\"" + fullName + "\",\"birthDate\":\"1990-01-01\",\"phone\":\"" + phone + "\"}";
    }

    @Test
    void create_and_read_back_a_patient() throws Exception {
        String token = registerAdmin("Clinica Alta", "patients-create@clinica.test");
        long id = createPatient(token, adultBody("Ada Lovelace", "10000001"));

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
    void a_duplicate_document_in_the_same_clinic_is_a_conflict_that_identifies_the_existing_patient() throws Exception {
        String token = registerAdmin("Clinica Dup", "patients-dup@clinica.test");
        long firstId = createPatient(token, adultBody("Primero", "20000002"));

        mockMvc.perform(post("/patients").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(adultBody("Segundo", "20000002")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("/errors/patient-document-taken"))
            .andExpect(jsonPath("$.existingPatientId").value((int) firstId));
    }

    @Test
    void two_patients_of_the_clinic_can_share_a_phone_and_the_second_is_flagged() throws Exception {
        String token = registerAdmin("Clinica Fono", "patients-phone@clinica.test");
        createPatient(token, bodyWithPhone("Madre", "+51999111222"));

        mockMvc.perform(post("/patients").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(bodyWithPhone("Hijo", "+51999111222")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.phoneSharedWithOtherPatients").value(true));
    }

    @Test
    void a_patient_without_own_phone_and_without_emergency_contact_is_rejected() throws Exception {
        String token = registerAdmin("Clinica SinFono", "patients-nophone@clinica.test");
        String body = "{\"fullName\":\"Adulto Mayor\",\"birthDate\":\"1940-01-01\"}";

        mockMvc.perform(post("/patients").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*].field").value(hasItem("emergencyContactName")));
    }

    @Test
    void a_patient_without_own_phone_but_with_emergency_contact_is_created() throws Exception {
        String token = registerAdmin("Clinica Emerg", "patients-emerg@clinica.test");
        String body = "{\"fullName\":\"Adulto Mayor\",\"birthDate\":\"1940-01-01\","
            + "\"emergencyContactName\":\"Hija\",\"emergencyContactPhone\":\"+51988877766\","
            + "\"emergencyContactRelationship\":\"Hija\"}";

        mockMvc.perform(post("/patients").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.phoneSharedWithOtherPatients").value(false));
    }

    @Test
    void a_patient_with_own_phone_does_not_need_an_emergency_contact() throws Exception {
        String token = registerAdmin("Clinica ConFono", "patients-withphone@clinica.test");

        mockMvc.perform(post("/patients").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(bodyWithPhone("Adulta", "+51955544433")))
            .andExpect(status().isCreated());
    }

    @Test
    void the_same_phone_in_another_clinic_is_not_flagged_as_shared() throws Exception {
        String tokenA = registerAdmin("Clinica Fono A", "patients-fono-a@clinica.test");
        String tokenB = registerAdmin("Clinica Fono B", "patients-fono-b@clinica.test");
        createPatient(tokenA, bodyWithPhone("En A", "+51900000000"));

        // Mismo teléfono en otra clínica: sin aviso de compartido, sin fuga entre clínicas.
        mockMvc.perform(post("/patients").header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON).content(bodyWithPhone("En B", "+51900000000")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.phoneSharedWithOtherPatients").value(false));
    }

    @Test
    void editing_a_patient_does_not_count_itself_as_a_shared_phone() throws Exception {
        String token = registerAdmin("Clinica Self", "patients-self@clinica.test");
        // Único paciente con ese teléfono: al editarlo, no debe contarse a sí mismo (usa AndIdNot).
        long id = createPatient(token, bodyWithPhone("Solitario", "+51911112222"));

        mockMvc.perform(put("/patients/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(bodyWithPhone("Solitario", "+51911112222")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phoneSharedWithOtherPatients").value(false));
    }

    @Test
    void removing_the_own_phone_on_edit_requires_an_emergency_contact() throws Exception {
        String token = registerAdmin("Clinica Quita", "patients-remove-phone@clinica.test");
        long id = createPatient(token, bodyWithPhone("Con Fono", "+51922223333"));

        // Editar quitando el teléfono y sin contacto de emergencia → mismo rechazo que en el alta.
        String noPhone = "{\"fullName\":\"Con Fono\",\"birthDate\":\"1990-01-01\"}";
        mockMvc.perform(put("/patients/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(noPhone))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*].field").value(hasItem("emergencyContactName")));
    }

    @Test
    void a_clinic_cannot_read_a_patient_of_another_clinic_even_by_id() throws Exception {
        String tokenA = registerAdmin("Clinica A HTTP", "patients-a@clinica.test");
        String tokenB = registerAdmin("Clinica B HTTP", "patients-b@clinica.test");
        long idInA = createPatient(tokenA, adultBody("Paciente de A", "30000003"));

        // B pide el paciente de A por su id EXACTO → 404, sin revelar que existe en otra clínica.
        mockMvc.perform(get("/patients/" + idInA).header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("/errors/patient-not-found"));
    }

    @Test
    void the_same_document_is_allowed_in_a_different_clinic() throws Exception {
        String tokenA = registerAdmin("Clinica Doc A", "patients-doc-a@clinica.test");
        String tokenB = registerAdmin("Clinica Doc B", "patients-doc-b@clinica.test");
        createPatient(tokenA, adultBody("Persona en A", "40000004"));

        // Misma persona, otra clínica: mismo documento, sin conflicto.
        createPatient(tokenB, adultBody("Persona en B", "40000004"));
    }

    @Test
    void the_list_only_shows_patients_of_the_active_clinic() throws Exception {
        String tokenA = registerAdmin("Clinica List A", "patients-list-a@clinica.test");
        String tokenB = registerAdmin("Clinica List B", "patients-list-b@clinica.test");
        createPatient(tokenA, adultBody("Solo en A", "50000005"));
        createPatient(tokenB, adultBody("Solo en B", "60000006"));

        mockMvc.perform(get("/patients").header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].fullName").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Solo en A"))))
            .andExpect(jsonPath("$.content[*].fullName").value(org.hamcrest.Matchers.hasItem("Solo en B")));
    }
}
