package com.odontorisas.presentation;

import com.jayway.jsonpath.JsonPath;
import com.odontorisas.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica el <b>documento OpenAPI que el backend publica de verdad</b> (no el snapshot
 * versionado): que declara el status real de cada operación y que los errores transversales
 * se inyectan solos.
 *
 * <p>Scenario "Los errores transversales se heredan sin declararlos": la clave está en que
 * {@code getBranding} declara <b>solo su 200</b>. Que aun así aparezca con {@code 401} y
 * {@code 500} es la prueba de que el mecanismo funciona <b>por defecto</b> — y de que una
 * capacidad futura ({@code patients}…) heredará sus errores sin escribir una línea.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractIT extends AbstractIntegrationTest {

    private static final String BRANDING = "/organizations/me/branding";

    @Autowired
    MockMvc mockMvc;

    private String apiDocs;

    @BeforeEach
    void fetchApiDocs() throws Exception {
        apiDocs = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private Map<String, Object> responsesOf(String path, String method) {
        return JsonPath.read(apiDocs, "$.paths['" + path + "']." + method + ".responses");
    }

    @Test
    void a_protected_operation_inherits_401_and_500_without_declaring_them() {
        // getBranding solo declara su 200: el 401 y el 500 los pone el customizer.
        assertThat(responsesOf(BRANDING, "get")).containsKeys("401", "500");
    }

    @Test
    void an_operation_restricted_by_role_inherits_403() {
        // updateBranding lleva @PreAuthorize("hasRole('ADMIN')").
        assertThat(responsesOf(BRANDING, "put")).containsKey("403");
    }

    @Test
    void a_public_operation_does_not_get_a_session_401() {
        // /auth/register es pública: no puede fallar por "sesión ausente", así que el customizer
        // no debe inventarle un 401. (El 401 semántico de login/refresh se declara aparte.)
        assertThat(responsesOf("/auth/register", "post")).doesNotContainKey("401");
    }

    @Test
    void every_operation_documents_the_unexpected_error() {
        assertThat(responsesOf(BRANDING, "get")).containsKey("500");
        assertThat(responsesOf("/auth/register", "post")).containsKey("500");
    }

    @Test
    void the_error_schemas_are_registered_so_the_refs_resolve() {
        Map<String, Object> schemas = JsonPath.read(apiDocs, "$.components.schemas");

        assertThat(schemas).containsKeys("ApiProblem", "ValidationProblem", "FieldError");
    }

    /**
     * Scenario "El status documentado coincide con el real": ejecuta la operación y compara el
     * status que devuelve de verdad con el que el documento declara. Es lo que impide que el
     * contrato vuelva a mentir sobre el status (que era el defecto original: decía 200 donde la
     * API devuelve 201 y 204).
     */
    @Test
    void the_documented_success_status_matches_the_real_one() throws Exception {
        // El registro devuelve 201 de verdad...
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationName\":\"Clínica Contrato\",\"email\":\"contrato@clinica.test\","
                    + "\"password\":\"password123\",\"fullName\":\"Ana Pérez\"}"))
            .andExpect(status().isCreated());
        // ...y eso es lo que documenta, sin dejar un 200 fantasma.
        assertThat(responsesOf("/auth/register", "post"))
            .containsKey("201")
            .doesNotContainKey("200");

        // El logout devuelve 204 de verdad...
        mockMvc.perform(post("/auth/logout"))
            .andExpect(status().isNoContent());
        // ...y eso documenta.
        assertThat(responsesOf("/auth/logout", "post"))
            .containsKey("204")
            .doesNotContainKey("200");
    }

    /** Las operaciones que sí devuelven 200 lo conservan (declararlas no debe borrar su schema). */
    @Test
    void operations_returning_200_keep_it_with_its_response_schema() {
        assertThat(responsesOf("/auth/login", "post")).containsKey("200");
        assertThat(responsesOf(BRANDING, "put")).containsKey("200");

        String loginOk = JsonPath.read(apiDocs, "$.paths['/auth/login'].post.responses.200").toString();
        assertThat(loginOk).contains("AuthResponse");
    }
}
