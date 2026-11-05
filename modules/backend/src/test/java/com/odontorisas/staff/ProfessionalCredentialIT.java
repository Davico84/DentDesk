package com.odontorisas.staff;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.Role;
import com.odontorisas.infra.security.TokenService;
import com.odontorisas.service.staff.ClinicalCapacityService;
import com.odontorisas.service.staff.ProfessionalCredentialService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Credencial propia y capacidad clínica por HTTP, contra PostgreSQL con la RLS aplicada (rol de app no
 * superusuario). Scenarios de {@code staff}: <i>Declarar la credencial profesional propia</i> y
 * <i>Capacidad clínica efectiva en la clínica activa</i>.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ProfessionalCredentialIT extends AbstractTenantAwareIntegrationTest {

    private static final String PATH = "/me/professional-credential";

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired ProfessionalCredentialService credentialService;
    @Autowired ClinicalCapacityService capacityService;

    private long clinicA;
    private long clinicB;
    private long admin;
    private long member;
    private long other;

    @BeforeEach
    void twoClinics() throws SQLException {
        String suffix = UUID.randomUUID().toString();
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            clinicA = id(s, "INSERT INTO organizations (name, slug) VALUES ('A', 'pc-a-" + suffix + "') RETURNING id");
            clinicB = id(s, "INSERT INTO organizations (name, slug) VALUES ('B', 'pc-b-" + suffix + "') RETURNING id");
            admin = user(s, "pc-admin-" + suffix);
            member = user(s, "pc-member-" + suffix);
            other = user(s, "pc-other-" + suffix);
            membership(s, admin, clinicA, "ADMIN");
            membership(s, member, clinicA, "MEMBER");
            membership(s, member, clinicB, "MEMBER");
            membership(s, other, clinicB, "MEMBER");
        }
    }

    @AfterEach
    void clearContext() {
        IdentityContext.clear();
        OrganizationContext.clear();
    }

    private static long id(Statement s, String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long user(Statement s, String name) throws SQLException {
        return id(s, "INSERT INTO users (email, password_hash, full_name) VALUES ('" + name
            + "@test.local', 'x', 'Dra. " + name + "') RETURNING id");
    }

    private static void membership(Statement s, long user, long org, String role) throws SQLException {
        s.execute("INSERT INTO memberships (user_id, organization_id, role, status) VALUES ("
            + user + ", " + org + ", '" + role + "', 'ACTIVE')");
    }

    private void sql(String statement) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute(statement);
        }
    }

    private String bearer(long user, long org, Role role) {
        return "Bearer " + tokenService.issueAccessToken(user, "u" + user + "@test.local", org, role);
    }

    private static String uniqueCop() {
        return String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
    }

    private static String body(String cop, LocalDate until, Boolean sworn) {
        return "{\"copNumber\":\"" + cop + "\",\"licensedUntil\":\"" + until + "\",\"sworn\":" + sworn + "}";
    }

    private LocalDate today() {
        return capacityService.todayInPeru();
    }

    private MvcResult declare(String bearer, String json) throws Exception {
        return mockMvc.perform(put(PATH).header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    // ================================================================ Declarar la credencial propia

    @Test
    void declaring_for_the_first_time_stores_it_and_it_is_valid_in_every_clinic() throws Exception {
        String cop = uniqueCop();
        mockMvc.perform(put(PATH).header("Authorization", bearer(member, clinicA, Role.MEMBER))
                .contentType(MediaType.APPLICATION_JSON).content(body(cop, today().plusDays(30), true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.credential.copNumber").value(cop))
            .andExpect(jsonPath("$.clinicalCapacity.state").value("ENABLED"));

        // La misma credencial al cambiar de clínica, sin volver a declararla.
        mockMvc.perform(get(PATH).header("Authorization", bearer(member, clinicB, Role.MEMBER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.credential.copNumber").value(cop))
            .andExpect(jsonPath("$.clinicalCapacity.state").value("ENABLED"));
    }

    @Test
    void renewing_an_expired_credential_enables_again() throws Exception {
        String cop = uniqueCop();
        sql("INSERT INTO professional_credential (user_id, cop_number, licensed_until, sworn_at) VALUES ("
            + member + ", '" + cop + "', '" + today().minusDays(1) + "', now())");
        String token = bearer(member, clinicA, Role.MEMBER);
        mockMvc.perform(get(PATH).header("Authorization", token))
            .andExpect(jsonPath("$.clinicalCapacity.state").value("EXPIRED"))
            .andExpect(jsonPath("$.clinicalCapacity.expiredOn").value(today().minusDays(1).toString()));

        assertThat(declare(token, body(cop, today().plusDays(365), true)).getResponse().getStatus()).isEqualTo(200);

        mockMvc.perform(get(PATH).header("Authorization", token))
            .andExpect(jsonPath("$.clinicalCapacity.state").value("ENABLED"))
            .andExpect(jsonPath("$.credential.licensedUntil").value(today().plusDays(365).toString()));
    }

    @Test
    void declaring_without_the_sworn_statement_is_rejected() throws Exception {
        String token = bearer(member, clinicA, Role.MEMBER);
        assertThat(declare(token, body(uniqueCop(), today(), false)).getResponse().getStatus()).isEqualTo(400);
        assertThat(declare(token, "{\"copNumber\":\"" + uniqueCop() + "\",\"licensedUntil\":\"" + today() + "\"}")
            .getResponse().getStatus()).isEqualTo(400);
        mockMvc.perform(get(PATH).header("Authorization", token)).andExpect(jsonPath("$.credential").isEmpty());
    }

    @Test
    void a_cop_number_with_letters_is_rejected_by_the_server() throws Exception {
        assertThat(declare(bearer(member, clinicA, Role.MEMBER), body("12A45", today(), true))
            .getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void a_past_date_is_rejected() throws Exception {
        mockMvc.perform(put(PATH).header("Authorization", bearer(member, clinicA, Role.MEMBER))
                .contentType(MediaType.APPLICATION_JSON).content(body(uniqueCop(), today().minusDays(1), true)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value(endsWith("/licensed-until-in-past")));
    }

    @Test
    void today_is_a_valid_date() throws Exception {
        assertThat(declare(bearer(member, clinicA, Role.MEMBER), body(uniqueCop(), today(), true))
            .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void a_cop_of_another_user_is_a_fixed_conflict_that_never_reaches_the_response_or_the_logs(CapturedOutput output)
            throws Exception {
        String taken = uniqueCop();
        sql("INSERT INTO professional_credential (user_id, cop_number, licensed_until, sworn_at) VALUES ("
            + other + ", '" + taken + "', '" + today().plusDays(10) + "', now())");
        String token = bearer(member, clinicA, Role.MEMBER);

        // Al declarar por primera vez…
        MvcResult first = declare(token, body(taken, today().plusDays(10), true));
        // …y al renovar cambiando el número a uno ajeno.
        assertThat(declare(token, body(uniqueCop(), today().plusDays(10), true)).getResponse().getStatus()).isEqualTo(200);
        MvcResult renewal = declare(token, body(taken, today().plusDays(10), true));

        for (MvcResult result : new MvcResult[] {first, renewal}) {
            String response = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(result.getResponse().getStatus()).isEqualTo(409);
            // Sin el número, sin quién (correo o nombre) y sin clínica. No se busca el id de la clínica como
            // texto: un id corto aparece dentro de la marca de tiempo.
            assertThat(response).contains("/credential-number-taken").doesNotContain(taken)
                .doesNotContain("pc-other").doesNotContain("organization");
        }
        // Hibernate loguea el error SQL, pero PostgreSQL omite el "Detail" con el valor de la clave cuando la
        // tabla tiene RLS (no filtra filas invisibles): el log trae solo el nombre de la restricción.
        assertThat(output.getAll()).contains("ux_professional_credential_cop_number").doesNotContain(taken);
    }

    @Test
    void a_platform_session_is_unauthenticated() throws Exception {
        String platform = "Bearer " + tokenService.issuePlatformAccessToken(1L, "root@platform.test");
        mockMvc.perform(get(PATH).header("Authorization", platform)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(PATH).header("Authorization", platform).contentType(MediaType.APPLICATION_JSON)
            .content(body(uniqueCop(), today(), true))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void the_session_user_is_the_only_one_that_can_be_written() throws Exception {
        // La API no tiene forma de nombrar a otro: aunque el ADMIN declare, la credencial es la suya.
        String cop = uniqueCop();
        assertThat(declare(bearer(admin, clinicA, Role.ADMIN), body(cop, today(), true)).getResponse().getStatus())
            .isEqualTo(200);
        mockMvc.perform(get(PATH).header("Authorization", bearer(member, clinicA, Role.MEMBER)))
            .andExpect(jsonPath("$.credential").isEmpty());
    }

    // ================================================================ Capacidad clínica efectiva

    @Test
    void without_a_credential_the_capacity_says_why() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", bearer(member, clinicA, Role.MEMBER)))
            .andExpect(jsonPath("$.credential").isEmpty())
            .andExpect(jsonPath("$.clinicalCapacity.state").value("NO_CREDENTIAL"));
    }

    @Test
    void revoked_in_clinic_a_and_enabled_in_clinic_b() throws Exception {
        sql("INSERT INTO professional_credential (user_id, cop_number, licensed_until, sworn_at) VALUES ("
            + member + ", '" + uniqueCop() + "', '" + today().plusDays(30) + "', now())");
        // El retiro lo escribe el ADMIN de A (la guarda de V30 lo exige), con su identidad en la sesión.
        sql("SELECT set_config('app.user_id', '" + admin + "', false); UPDATE memberships SET "
            + "clinical_capacity_revoked_at = now(), clinical_capacity_revoked_by = " + admin
            + " WHERE user_id = " + member + " AND organization_id = " + clinicA);

        mockMvc.perform(get(PATH).header("Authorization", bearer(member, clinicA, Role.MEMBER)))
            .andExpect(jsonPath("$.clinicalCapacity.state").value("REVOKED"));
        mockMvc.perform(get(PATH).header("Authorization", bearer(member, clinicB, Role.MEMBER)))
            .andExpect(jsonPath("$.clinicalCapacity.state").value("ENABLED"));
    }

    @Test
    void a_deactivated_member_with_a_live_session_has_no_capacity() throws Exception {
        sql("INSERT INTO professional_credential (user_id, cop_number, licensed_until, sworn_at) VALUES ("
            + member + ", '" + uniqueCop() + "', '" + today().plusDays(30) + "', now())");
        String liveToken = bearer(member, clinicA, Role.MEMBER);
        sql("UPDATE memberships SET status = 'INACTIVE' WHERE user_id = " + member + " AND organization_id = " + clinicA);

        mockMvc.perform(get(PATH).header("Authorization", liveToken))
            .andExpect(jsonPath("$.clinicalCapacity.state").value("INACTIVE_MEMBERSHIP"));
    }

    @Test
    void without_an_active_clinic_the_credential_is_shown_and_saved_but_the_capacity_is_empty() {
        // Las sesiones de clínica siempre llevan organización; el caso sin ella es del servicio (login, registro).
        IdentityContext.set(member);
        String cop = uniqueCop();

        var saved = credentialService.declare(cop, today().plusDays(5));
        var read = credentialService.mine();

        assertThat(saved.credential()).isPresent();
        assertThat(read.credential()).get().extracting(ProfessionalCredentialService.CredentialView::copNumber)
            .isEqualTo(cop);
        assertThat(read.clinicalCapacity()).isEmpty();
    }
}
