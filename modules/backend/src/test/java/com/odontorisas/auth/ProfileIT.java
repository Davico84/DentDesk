package com.odontorisas.auth;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.Role;
import com.odontorisas.infra.security.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil propio por HTTP ({@code update-authentication-my-profile}). Scenarios de {@code authentication}:
 * <i>Ver el perfil propio</i> y <i>Corregir el nombre propio</i>. "Lo ya firmado conserva el nombre de
 * entonces" vive en {@code SigningWithCredentialIT}, que ya sabe sellar.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileIT extends AbstractTenantAwareIntegrationTest {

    private static final String PATH = "/me/profile";

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;

    private long clinic;
    private long admin;
    private long member;
    private String adminEmail;
    private String memberEmail;

    @BeforeEach
    void aClinic() throws SQLException {
        String suffix = UUID.randomUUID().toString();
        adminEmail = "pf-admin-" + suffix + "@test.local";
        memberEmail = "pf-member-" + suffix + "@test.local";
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            clinic = id(s, "INSERT INTO organizations (name, slug) VALUES ('P', 'pf-" + suffix + "') RETURNING id");
            admin = id(s, "INSERT INTO users (email, password_hash, full_name) VALUES ('" + adminEmail
                + "', 'x', 'Dra. Admin') RETURNING id");
            member = id(s, "INSERT INTO users (email, password_hash, full_name) VALUES ('" + memberEmail
                + "', 'x', 'Dr. Miembro') RETURNING id");
            s.execute("INSERT INTO memberships (user_id, organization_id, role, status) VALUES ("
                + admin + ", " + clinic + ", 'ADMIN', 'ACTIVE')");
            s.execute("INSERT INTO memberships (user_id, organization_id, role, status) VALUES ("
                + member + ", " + clinic + ", 'MEMBER', 'ACTIVE')");
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

    private String column(long user, String column) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT " + column + " FROM users WHERE id = " + user)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private String bearer(long user, Role role) {
        return "Bearer " + tokenService.issueAccessToken(user, "u" + user + "@test.local", clinic, role);
    }

    private static String nameBody(String fullName) {
        return "{\"fullName\":\"" + fullName + "\"}";
    }

    // ================================================================ Ver el perfil propio

    @Test
    void shows_the_own_name_and_email() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", bearer(member, Role.MEMBER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Dr. Miembro"))
            .andExpect(jsonPath("$.email").value(memberEmail));
    }

    @Test
    void the_profile_is_always_the_own_even_as_admin_naming_another_user() throws Exception {
        // No hay forma de nombrar a otro: ni parámetro ni campo de id. Se intenta igual.
        mockMvc.perform(get(PATH).param("userId", String.valueOf(member)).param("id", String.valueOf(member))
                .header("Authorization", bearer(admin, Role.ADMIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(adminEmail));

        mockMvc.perform(patch(PATH).header("Authorization", bearer(admin, Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + member + ",\"userId\":" + member + ",\"fullName\":\"Dra. Admin Nueva\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(adminEmail));

        assertThat(column(admin, "full_name")).isEqualTo("Dra. Admin Nueva");
        assertThat(column(member, "full_name")).isEqualTo("Dr. Miembro");
    }

    @Test
    void without_a_session_it_is_unauthenticated() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON).content(nameBody("Nadie")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void a_platform_session_is_unauthenticated() throws Exception {
        String platform = "Bearer " + tokenService.issuePlatformAccessToken(1L, "root@platform.test");
        mockMvc.perform(get(PATH).header("Authorization", platform)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(PATH).header("Authorization", platform)
                .contentType(MediaType.APPLICATION_JSON).content(nameBody("Root")))
            .andExpect(status().isUnauthorized());
    }

    // ================================================================ Corregir el nombre propio

    @Test
    void correcting_the_name_saves_it_without_the_outer_spaces() throws Exception {
        mockMvc.perform(patch(PATH).header("Authorization", bearer(member, Role.MEMBER))
                .contentType(MediaType.APPLICATION_JSON).content(nameBody("  Dr. Miembro Corregido  ")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Dr. Miembro Corregido"))
            .andExpect(jsonPath("$.email").value(memberEmail));

        assertThat(column(member, "full_name")).isEqualTo("Dr. Miembro Corregido");
    }

    @Test
    void a_name_too_short_too_long_or_only_spaces_is_rejected() throws Exception {
        String tooLong = "a".repeat(121);
        for (String invalid : new String[] {"A", " A ", "   ", "", tooLong}) {
            mockMvc.perform(patch(PATH).header("Authorization", bearer(member, Role.MEMBER))
                    .contentType(MediaType.APPLICATION_JSON).content(nameBody(invalid)))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(patch(PATH).header("Authorization", bearer(member, Role.MEMBER))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());

        assertThat(column(member, "full_name")).isEqualTo("Dr. Miembro");
    }

    @Test
    void the_limits_count_without_the_outer_spaces() throws Exception {
        // 120 caracteres útiles con espacios alrededor: válido.
        String max = "b".repeat(120);
        mockMvc.perform(patch(PATH).header("Authorization", bearer(member, Role.MEMBER))
                .contentType(MediaType.APPLICATION_JSON).content(nameBody("   " + max + "   ")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value(max));
    }

    @Test
    void an_email_in_the_body_does_not_change_the_email() throws Exception {
        mockMvc.perform(patch(PATH).header("Authorization", bearer(member, Role.MEMBER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Dr. Miembro\",\"email\":\"otro@test.local\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(memberEmail));

        assertThat(column(member, "email")).isEqualTo(memberEmail);
    }
}
