package com.odontorisas.branding;

import com.jayway.jsonpath.JsonPath;
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

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    /** Registra una organización + admin y devuelve el access token (rol ADMIN). */
    private String registerAdmin(String email) throws Exception {
        String body = invitedAdminRegisterBody("Clínica Marca", email, "password123", "Ana Pérez");
        MvcResult result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");
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
                .content("{\"brandPreset\":\"TEAL\",\"logoUrl\":\"https://cdn.test/logo.png\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.brandPreset").value("TEAL"))
            .andExpect(jsonPath("$.logoUrl").value("https://cdn.test/logo.png"));

        mockMvc.perform(get("/organizations/me/branding").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.brandPreset").value("TEAL"));
    }

    @Test
    void clearing_all_fields_removes_personalization() throws Exception {
        String token = registerAdmin("brand-clear@clinica.test");

        mockMvc.perform(put("/organizations/me/branding").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"brandPreset\":\"TEAL\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(put("/organizations/me/branding").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.brandPreset").doesNotExist());

        mockMvc.perform(get("/organizations/me/branding").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.brandPreset").doesNotExist());
    }

    /**
     * Scenario "Un error de permisos usa el formato común": el 403 de {@code @PreAuthorize} ya era
     * RFC 9457 (pasa por GlobalExceptionHandler). Se fija por test para que siga compartiendo forma
     * con el resto de errores.
     */
    @Test
    void non_admin_cannot_update_branding() throws Exception {
        // Token válido en firma pero con rol USER (no existe endpoint para crear USER aún).
        String userToken = tokenService.issueAccessToken(999L, "user@clinica.test", 999L, Role.MEMBER);

        mockMvc.perform(put("/organizations/me/branding").header("Authorization", bearer(userToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"brandPreset\":\"TEAL\"}"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("/errors/access-denied"))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void invalid_preset_key_is_rejected() throws Exception {
        String token = registerAdmin("brand-badpreset@clinica.test");

        mockMvc.perform(put("/organizations/me/branding").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"brandPreset\":\"ROJO\"}"))
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
