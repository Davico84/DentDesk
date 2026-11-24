package com.odontorisas.appointment;

import com.jayway.jsonpath.JsonPath;
import com.odontorisas.AbstractIntegrationTest;
import com.odontorisas.ClinicalSignerFixtures;
import com.odontorisas.common.Role;
import com.odontorisas.infra.security.AccessClaims;
import com.odontorisas.infra.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La API de la agenda a nivel HTTP contra PostgreSQL real (add-appointments tarea 3.7): sesión y organización,
 * aislamiento entre clínicas, validación y el contrato de errores.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AppointmentControllerIT extends AbstractIntegrationTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    TokenService tokenService;

    /** Una clínica registrada por HTTP: su ADMIN (habilitado como odontólogo) y un paciente. */
    private record Clinic(String bearer, String email, long orgId, long adminId, long patientId) {
    }

    private Clinic clinicA;
    private Clinic clinicB;
    private LocalDate tuesday;

    @BeforeEach
    void twoClinics() throws Exception {
        clinicA = clinic("Clínica A");
        clinicB = clinic("Clínica B");
        tuesday = LocalDate.now(LIMA).with(TemporalAdjusters.next(DayOfWeek.TUESDAY)).plusWeeks(1);
    }

    private Clinic clinic(String name) throws Exception {
        String email = "ap-" + UUID.randomUUID() + "@clinica.test";
        MvcResult result = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(invitedAdminRegisterBody(name, email, "password123", "Dra. Admin")))
            .andExpect(status().isCreated())
            .andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");
        AccessClaims claims = tokenService.verifyAccessToken(token);
        long patientId;
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            ClinicalSignerFixtures.enableSigner(owner, claims.userId(), claims.organizationId());
            try (ResultSet rs = s.executeQuery("INSERT INTO patients (organization_id, full_name, birth_date, status, "
                    + "created_by, updated_by) VALUES (" + claims.organizationId() + ", 'Paciente', '1990-01-01', 'ACTIVE', "
                    + claims.userId() + ", " + claims.userId() + ") RETURNING id")) {
                rs.next();
                patientId = rs.getLong(1);
            }
        }
        return new Clinic("Bearer " + token, email, claims.organizationId(), claims.userId(), patientId);
    }

    private Instant at(String time) {
        return tuesday.atTime(java.time.LocalTime.parse(time)).atZone(LIMA).toInstant();
    }

    private static String body(long patientId, long dentistId, Instant startsAt, int minutes) {
        return "{\"patientId\":" + patientId + ",\"dentistUserId\":" + dentistId + ",\"startsAt\":\"" + startsAt
            + "\",\"durationMinutes\":" + minutes + "}";
    }

    private long book(Clinic clinic, String time) throws Exception {
        MvcResult result = mockMvc.perform(post("/appointments").header("Authorization", clinic.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body(clinic.patientId(), clinic.adminId(), at(time), 30)))
            .andExpect(status().isCreated())
            .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id")).longValue();
    }

    private int appointmentsIn(long orgId) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM appointments WHERE organization_id = " + orgId)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private String week() {
        return "/appointments?from=" + tuesday.minusDays(1) + "&to=" + tuesday.plusDays(5);
    }

    @Test
    void a_member_schedules_and_reads_the_week() throws Exception {
        long id = book(clinicA, "10:00");

        mockMvc.perform(get(week()).header("Authorization", clinicA.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(id))
            .andExpect(jsonPath("$[0].status").value("AGENDADA"))
            .andExpect(jsonPath("$[0].durationMinutes").value(30))
            .andExpect(jsonPath("$[0].patientName").value("Paciente"))
            .andExpect(jsonPath("$[0].dentistName").value("Dra. Admin"));
        mockMvc.perform(get("/appointments/dentists").header("Authorization", clinicA.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].userId").value(clinicA.adminId()));
    }

    // --- Sesión y organización ---

    @Test
    void without_a_session_the_agenda_is_unauthorized() throws Exception {
        mockMvc.perform(get(week())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/business-hours")).andExpect(status().isUnauthorized());
    }

    @Test
    void a_token_without_an_active_organization_is_unauthorized() throws Exception {
        String noOrg = "Bearer " + tokenService.issueAccessToken(clinicA.adminId(), clinicA.email(), null, Role.ADMIN);

        mockMvc.perform(get(week()).header("Authorization", noOrg)).andExpect(status().isUnauthorized());
    }

    @Test
    void a_platform_session_is_unauthorized() throws Exception {
        String platform = "Bearer " + tokenService.issuePlatformAccessToken(1L, "root@platform.test");

        mockMvc.perform(get(week()).header("Authorization", platform)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/business-hours").header("Authorization", platform)).andExpect(status().isUnauthorized());
    }

    @Test
    void an_inactive_membership_cannot_get_a_session_for_the_clinic() throws Exception {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("SELECT set_config('app.user_id', '" + clinicA.adminId() + "', false); UPDATE memberships SET "
                + "status = 'INACTIVE' WHERE user_id = " + clinicA.adminId());
        }

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + clinicA.email() + "\",\"password\":\"password123\"}"))
            .andExpect(status().isUnauthorized());
    }

    // --- Aislamiento entre clínicas ---

    @Test
    void an_appointment_of_another_clinic_is_not_found_and_not_modified() throws Exception {
        long foreign = book(clinicB, "10:00");

        mockMvc.perform(get("/appointments/" + foreign).header("Authorization", clinicA.bearer()))
            .andExpect(status().isNotFound());
        mockMvc.perform(put("/appointments/" + foreign + "/schedule").header("Authorization", clinicA.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dentistUserId\":" + clinicA.adminId() + ",\"startsAt\":\"" + at("11:00")
                    + "\",\"durationMinutes\":30}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/appointments/" + foreign + "/cancel").header("Authorization", clinicA.bearer()))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/appointments/" + foreign).header("Authorization", clinicB.bearer()))
            .andExpect(jsonPath("$.status").value("AGENDADA"))
            .andExpect(jsonPath("$.startsAt").value(at("10:00").toString()));
    }

    @Test
    void a_patient_of_another_clinic_is_not_found_and_nothing_is_written() throws Exception {
        mockMvc.perform(post("/appointments").header("Authorization", clinicA.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(clinicB.patientId(), clinicA.adminId(), at("10:00"), 30)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("/errors/patient-not-found"));
        assertThat(appointmentsIn(clinicA.orgId())).isZero();
    }

    @Test
    void a_dentist_who_is_not_a_member_is_a_conflict_and_nothing_is_written() throws Exception {
        mockMvc.perform(post("/appointments").header("Authorization", clinicA.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(clinicA.patientId(), clinicB.adminId(), at("10:00"), 30)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("/errors/dentist-not-schedulable"));

        long own = book(clinicA, "10:00");
        mockMvc.perform(put("/appointments/" + own + "/schedule").header("Authorization", clinicA.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dentistUserId\":" + clinicB.adminId() + ",\"startsAt\":\"" + at("11:00")
                    + "\",\"durationMinutes\":30}"))
            .andExpect(status().isConflict());
        mockMvc.perform(get("/appointments/" + own).header("Authorization", clinicA.bearer()))
            .andExpect(jsonPath("$.dentistUserId").value(clinicA.adminId()));
    }

    // --- Validación y errores ---

    @Test
    void a_range_longer_than_seven_days_or_inverted_is_a_bad_request() throws Exception {
        mockMvc.perform(get("/appointments?from=" + tuesday + "&to=" + tuesday.plusDays(7))
                .header("Authorization", clinicA.bearer()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("to"));
        mockMvc.perform(get("/appointments?from=" + tuesday + "&to=" + tuesday.minusDays(1))
                .header("Authorization", clinicA.bearer()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void a_missing_dentist_or_invalid_duration_is_a_bad_request_with_the_field() throws Exception {
        mockMvc.perform(post("/appointments").header("Authorization", clinicA.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":" + clinicA.patientId() + ",\"startsAt\":\"" + at("10:00")
                    + "\",\"durationMinutes\":30}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("dentistUserId"));
        mockMvc.perform(post("/appointments").header("Authorization", clinicA.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body(clinicA.patientId(), clinicA.adminId(), at("10:00"), 300)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("durationMinutes"));
        mockMvc.perform(post("/appointments").header("Authorization", clinicA.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body(clinicA.patientId(), clinicA.adminId(), at("10:00"), 7)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("durationMinutes"));
        mockMvc.perform(post("/appointments").header("Authorization", clinicA.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body(clinicA.patientId(), clinicA.adminId(), at("10:03"), 30)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("startsAt"));
        assertThat(appointmentsIn(clinicA.orgId())).isZero();
    }

    @Test
    void business_rule_errors_use_their_status_and_type() throws Exception {
        book(clinicA, "10:00");

        mockMvc.perform(post("/appointments").header("Authorization", clinicA.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body(clinicA.patientId(), clinicA.adminId(), at("10:15"), 30)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("/errors/appointment-conflict"));
        mockMvc.perform(post("/appointments").header("Authorization", clinicA.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body(clinicA.patientId(), clinicA.adminId(), at("17:45"), 30)))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.type").value("/errors/outside-business-hours"));
    }

    @Test
    void cancelling_and_marking_before_the_start_follow_the_state_rules() throws Exception {
        long id = book(clinicA, "10:00");

        mockMvc.perform(post("/appointments/" + id + "/attended").header("Authorization", clinicA.bearer()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("/errors/appointment-not-started"));
        mockMvc.perform(post("/appointments/" + id + "/cancel").header("Authorization", clinicA.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"El paciente pidió otro día\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELADA"))
            .andExpect(jsonPath("$.cancellationReason").value("El paciente pidió otro día"))
            .andExpect(jsonPath("$.statusChangedByName").value("Dra. Admin"));
        mockMvc.perform(post("/appointments/" + id + "/cancel").header("Authorization", clinicA.bearer()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("/errors/appointment-closed"));
    }

    @Test
    void attending_with_a_note_without_clinical_capacity_is_forbidden() throws Exception {
        long id = book(clinicA, "10:00");
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("DELETE FROM professional_credential WHERE user_id = " + clinicA.adminId());
        }

        mockMvc.perform(post("/appointments/" + id + "/consultation-note").header("Authorization", clinicA.bearer()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.type").value("/errors/clinical-capacity-required"))
            .andExpect(jsonPath("$.reason").value("NO_CREDENTIAL"));
    }
}
