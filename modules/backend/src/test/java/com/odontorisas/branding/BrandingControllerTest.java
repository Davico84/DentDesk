package com.odontorisas.branding;

import com.odontorisas.infra.security.JwtAuthenticationFilter;
import com.odontorisas.presentation.controller.BrandingController;
import com.odontorisas.service.organization.Branding;
import com.odontorisas.service.organization.BrandingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice de <b>controller + seguridad por rol</b> (docs/testing.md §5, hueco cerrado):
 * {@code BrandingController} es el único endpoint del proyecto con {@code @PreAuthorize} — el
 * candidato natural para probar autorización por rol de verdad, algo que hoy no cubre ningún
 * test (los {@code *ControllerIT} pasan por JWT real, pero ninguno ejercita un usuario
 * autenticado <b>sin</b> el rol requerido).
 *
 * <p>Se importa {@code MethodSecurityTestConfig} (solo {@code @EnableMethodSecurity}) porque el
 * slice no carga la {@code SecurityConfig} real de la app — sin esto {@code @PreAuthorize} no se
 * evaluaría y el test de "no-admin" pasaría en falso. Se excluye {@code JwtAuthenticationFilter}
 * (necesita {@code TokenService}, ausente en el slice) y se usa {@code @WithMockUser} para
 * simular la identidad/rol, sin construir un JWT real.
 */
@WebMvcTest(controllers = BrandingController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(BrandingControllerTest.MethodSecurityTestConfig.class)
class BrandingControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrandingService brandingService;

    private static final String VALID_BODY = "{\"brandPrimary\":\"#7f1aa6\"}";

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_can_update_branding() throws Exception {
        given(brandingService.update(any())).willReturn(Branding.empty());

        mockMvc.perform(put("/organizations/me/branding")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void a_non_admin_gets_403_and_the_service_is_never_called() throws Exception {
        mockMvc.perform(put("/organizations/me/branding")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
            .andExpect(status().isForbidden());

        verifyNoInteractions(brandingService);
    }

    @Test
    @WithMockUser
    void reading_the_branding_does_not_require_the_admin_role() throws Exception {
        given(brandingService.getForCurrentOrganization()).willReturn(Branding.empty());

        mockMvc.perform(get("/organizations/me/branding"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void an_invalid_color_returns_400_validation_problem() throws Exception {
        mockMvc.perform(put("/organizations/me/branding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"brandPrimary\":\"not-a-color\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItem("brandPrimary")));
    }
}
