package com.odontorisas.staff;

import com.jayway.jsonpath.JsonPath;
import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.ClinicalSignerFixtures;
import com.odontorisas.common.Role;
import com.odontorisas.infra.security.TokenService;
import com.odontorisas.service.staff.ClinicalCapacityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Retiro y restitución de la capacidad clínica y su estado en el listado del personal, por HTTP y con la RLS
 * aplicada. Scenarios de {@code staff}: <i>Retirar y restituir la capacidad clínica de un miembro</i> y
 * <i>Capacidad clínica en el listado del personal</i>.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaffClinicalCapacityIT extends AbstractTenantAwareIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired ClinicalCapacityService capacityService;

    private long clinicA;
    private long clinicB;
    private long admin;
    private long otherAdmin;
    private long enabled;
    private long expired;
    private long withoutCredential;
    private long outsider;
    private String enabledCop;
    private String outsiderCop;

    @BeforeEach
    void aClinicWithEveryCapacityState() throws SQLException {
        String suffix = UUID.randomUUID().toString();
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            clinicA = id(s, "INSERT INTO organizations (name, slug) VALUES ('A', 'scc-a-" + suffix + "') RETURNING id");
            clinicB = id(s, "INSERT INTO organizations (name, slug) VALUES ('B', 'scc-b-" + suffix + "') RETURNING id");
            admin = user(s, "scc-admin-" + suffix);
            otherAdmin = user(s, "scc-admin2-" + suffix);
            enabled = user(s, "scc-enabled-" + suffix);
            expired = user(s, "scc-expired-" + suffix);
            withoutCredential = user(s, "scc-nocred-" + suffix);
            outsider = user(s, "scc-outsider-" + suffix);
            membership(s, admin, clinicA, "ADMIN");
            membership(s, otherAdmin, clinicA, "ADMIN");
            membership(s, enabled, clinicA, "MEMBER");
            membership(s, expired, clinicA, "MEMBER");
            membership(s, withoutCredential, clinicA, "MEMBER");
            membership(s, outsider, clinicB, "MEMBER");
            enabledCop = ClinicalSignerFixtures.uniqueCop();
            outsiderCop = ClinicalSignerFixtures.uniqueCop();
            credential(s, enabled, enabledCop, "current_date + 30");
            credential(s, expired, ClinicalSignerFixtures.uniqueCop(), "'" + capacityService.todayInPeru().minusDays(3) + "'");
            credential(s, outsider, outsiderCop, "current_date + 30");
        }
    }

    private static long id(Statement s, String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long user(Statement s, String name) throws SQLException {
        return id(s, "INSERT INTO users (email, password_hash, full_name) VALUES ('" + name
            + "@test.local', 'x', '" + name + "') RETURNING id");
    }

    private static void membership(Statement s, long user, long org, String role) throws SQLException {
        s.execute("INSERT INTO memberships (user_id, organization_id, role, status) VALUES ("
            + user + ", " + org + ", '" + role + "', 'ACTIVE')");
    }

    private static void credential(Statement s, long user, String cop, String until) throws SQLException {
        s.execute("INSERT INTO professional_credential (user_id, cop_number, licensed_until, sworn_at) VALUES ("
            + user + ", '" + cop + "', " + until + ", now())");
    }

    private String bearer(long user, long org, Role role) {
        return "Bearer " + tokenService.issueAccessToken(user, "u" + user + "@test.local", org, role);
    }

    private String adminA() {
        return bearer(admin, clinicA, Role.ADMIN);
    }

    private RequestBuilder setRevoked(String bearer, long member, boolean revoked) {
        return patch("/staff/members/" + member + "/clinical-capacity").header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON).content("{\"revoked\":" + revoked + "}");
    }

    private String stateOf(long user, long org) throws Exception {
        String body = mockMvc.perform(get("/me/professional-credential").header("Authorization",
            bearer(user, org, Role.MEMBER))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.clinicalCapacity.state");
    }

    private String copOf(long user) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT cop_number FROM professional_credential WHERE user_id = " + user)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    // ================================================================ Retirar y restituir

    @Test
    void revoking_removes_the_capacity_in_that_clinic_without_touching_the_credential() throws Exception {
        mockMvc.perform(setRevoked(adminA(), enabled, true)).andExpect(status().isNoContent());

        assertThat(stateOf(enabled, clinicA)).isEqualTo("REVOKED");
        assertThat(copOf(enabled)).isEqualTo(enabledCop);
    }

    @Test
    void restoring_gives_the_capacity_back() throws Exception {
        mockMvc.perform(setRevoked(adminA(), enabled, true)).andExpect(status().isNoContent());
        mockMvc.perform(setRevoked(adminA(), enabled, false)).andExpect(status().isNoContent());

        assertThat(stateOf(enabled, clinicA)).isEqualTo("ENABLED");
    }

    @Test
    void restoring_does_not_grant_what_the_credential_does_not_give() throws Exception {
        mockMvc.perform(setRevoked(adminA(), expired, true)).andExpect(status().isNoContent());
        mockMvc.perform(setRevoked(adminA(), withoutCredential, true)).andExpect(status().isNoContent());
        mockMvc.perform(setRevoked(adminA(), expired, false)).andExpect(status().isNoContent());
        mockMvc.perform(setRevoked(adminA(), withoutCredential, false)).andExpect(status().isNoContent());

        assertThat(stateOf(expired, clinicA)).isEqualTo("EXPIRED");
        assertThat(stateOf(withoutCredential, clinicA)).isEqualTo("NO_CREDENTIAL");
    }

    @Test
    void the_admin_cannot_revoke_itself_or_another_admin() throws Exception {
        mockMvc.perform(setRevoked(adminA(), admin, true)).andExpect(status().isForbidden());
        mockMvc.perform(setRevoked(adminA(), otherAdmin, true)).andExpect(status().isForbidden());
    }

    @Test
    void a_member_cannot_revoke_or_restore() throws Exception {
        String member = bearer(enabled, clinicA, Role.MEMBER);
        mockMvc.perform(setRevoked(member, expired, true)).andExpect(status().isForbidden());
        mockMvc.perform(setRevoked(member, enabled, false)).andExpect(status().isForbidden());
    }

    @Test
    void someone_outside_the_clinic_is_not_found() throws Exception {
        mockMvc.perform(setRevoked(adminA(), outsider, true))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value(endsWith("/member-not-found")));
        assertThat(stateOf(outsider, clinicB)).isEqualTo("ENABLED");
    }

    // ================================================================ Listado del personal

    @Test
    void the_staff_list_shows_each_capacity_state_and_never_a_credential_from_another_clinic() throws Exception {
        mockMvc.perform(setRevoked(adminA(), withoutCredential, true)).andExpect(status().isNoContent());

        String body = mockMvc.perform(get("/staff/members").header("Authorization", adminA()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Map<String, Object>> members = JsonPath.read(body, "$[*]");

        Map<Long, Map<String, Object>> capacityById = new java.util.HashMap<>();
        for (Map<String, Object> m : members) {
            @SuppressWarnings("unchecked")
            Map<String, Object> capacity = (Map<String, Object>) m.get("clinicalCapacity");
            capacityById.put(((Number) m.get("id")).longValue(), capacity);
        }
        assertThat(capacityById.get(enabled)).containsEntry("state", "ENABLED").containsEntry("copNumber", enabledCop);
        assertThat(capacityById.get(expired)).containsEntry("state", "EXPIRED")
            .containsEntry("expiredOn", capacityService.todayInPeru().minusDays(3).toString());
        assertThat(capacityById.get(withoutCredential)).containsEntry("state", "REVOKED")
            .containsEntry("copNumber", null);
        assertThat(capacityById).doesNotContainKey(outsider);
        assertThat(body).doesNotContain(outsiderCop);
    }
}
