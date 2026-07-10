package com.odontorisas.branding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odontorisas.AbstractIntegrationTest;
import com.odontorisas.common.Role;
import com.odontorisas.infra.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenarios de organization-branding a nivel HTTP (contra PostgreSQL real). El registro crea
 * un ADMIN; para el caso "no ADMIN" se emite un token con rol USER.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BrandingControllerIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TokenService tokenService;

    @Autowired
    ObjectMapper objectMapper;

    /** Registra una organización + admin y devuelve el access token (rol ADMIN). */
    private String registerAdmin(String email) throws Exception {
        String body = "{\"organizationName\":\"Clínica Marca\",\"email\":\"" + email
            + "\",\"password\":\"password123\",\"fullName\":\"Ana Pérez\"}";
        MvcResult result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void admin_updates_branding_and_reads_it_back() throws Exception {
        String token = registerAdmin("brand-admin@clinica.test");

        mockMvc.perform(put("/organizations/me/branding")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"brandPrimary\":\"#123abc\",\"logoUrl\":\"https://cdn.test/logo.png\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.brandPrimary").value("#123abc"))
            .andExpect(jsonPath("$.logoUrl").value("https://cdn.test/logo.png"));

        mockMvc.perform(get("/organizations/me/branding").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.brandPrimary").value("#123abc"));
    }

    @Test
    void clearing_all_fields_removes_personalization() throws Exception {
        String token = registerAdmin("brand-clear@clinica.test");

        mockMvc.perform(put("/organizations/me/branding").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"brandPrimary\":\"#123abc\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(put("/organizations/me/branding").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.brandPrimary").doesNotExist());

        mockMvc.perform(get("/organizations/me/branding").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.brandPrimary").doesNotExist());
    }

    @Test
    void non_admin_cannot_update_branding() throws Exception {
        // Token válido en firma pero con rol USER (no existe endpoint para crear USER aún).
        String userToken = tokenService.issueAccessToken(999L, "user@clinica.test", 999L, Role.USER);

        mockMvc.perform(put("/organizations/me/branding").header("Authorization", bearer(userToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"brandPrimary\":\"#123abc\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void invalid_color_is_rejected() throws Exception {
        String token = registerAdmin("brand-badcolor@clinica.test");

        mockMvc.perform(put("/organizations/me/branding").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"brandPrimary\":\"rojo\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void non_https_logo_url_is_rejected() throws Exception {
        String token = registerAdmin("brand-badurl@clinica.test");

        mockMvc.perform(put("/organizations/me/branding").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"logoUrl\":\"javascript:alert(1)\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(put("/organizations/me/branding").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"logoUrl\":\"http://cdn.test/logo.png\"}"))
            .andExpect(status().isBadRequest());
    }
}
