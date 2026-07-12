package com.odontorisas.presentation;

import com.jayway.jsonpath.JsonPath;
import com.odontorisas.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenario "El schema de error describe el cuerpo real": fija que el cuerpo que la API devuelve
 * de verdad <b>solo contiene campos descritos</b> por los schemas {@code ApiProblem} /
 * {@code ValidationProblem}, y que <b>no se documenta nada que la API nunca emita</b>.
 *
 * <p>Es la mitigación del riesgo asumido en el design: los schemas son "solo documentación", así
 * que podrían derivar del cuerpo real. Si alguien cambia el manejo de errores, este test rompe
 * antes de que el contrato empiece a mentir.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorContractIT extends AbstractIntegrationTest {

    /** Campos de `ApiProblem`. `instance` NO está: la API nunca lo emite. */
    private static final Set<String> API_PROBLEM_FIELDS =
        Set.of("type", "title", "status", "detail", "timestamp", "traceId");

    /** `ValidationProblem` = lo anterior + la extensión `errors`. */
    private static final Set<String> VALIDATION_PROBLEM_FIELDS =
        Set.of("type", "title", "status", "detail", "timestamp", "traceId", "errors");

    @Autowired
    MockMvc mockMvc;

    private Map<String, Object> errorBodyOf(String content) {
        return JsonPath.read(content, "$");
    }

    @Test
    void validation_error_body_only_contains_documented_fields() throws Exception {
        String body = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationName\":\"C\",\"email\":\"no-es-email\",\"password\":\"1\",\"fullName\":\"A\"}"))
            .andExpect(status().isBadRequest())
            .andReturn().getResponse().getContentAsString();

        Map<String, Object> problem = errorBodyOf(body);

        assertThat(problem.keySet()).isSubsetOf(VALIDATION_PROBLEM_FIELDS);
        assertThat(problem).containsKeys("type", "title", "status", "detail", "timestamp", "errors");
        // `instance` está documentado en RFC 9457 pero la API NO lo emite: no debe aparecer.
        assertThat(problem).doesNotContainKey("instance");

        // El detalle por campo es lo que permite pintar el error junto al input.
        List<Map<String, String>> errors = JsonPath.read(body, "$.errors");
        assertThat(errors).isNotEmpty();
        assertThat(errors.getFirst()).containsOnlyKeys("field", "message");
    }

    /**
     * La otra forma del 400: JSON malformado. Lo resuelve la clase padre del handler y <b>no</b>
     * lleva `errors` — de ahí que `errors` sea OPCIONAL en el schema. Además comprueba que ese
     * error también recibe las extensiones comunes (`timestamp`), pese a no pasar por la fábrica.
     */
    @Test
    void malformed_json_produces_a_400_without_the_errors_extension() throws Exception {
        String body = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{ esto no es json "))
            .andExpect(status().isBadRequest())
            .andReturn().getResponse().getContentAsString();

        Map<String, Object> problem = errorBodyOf(body);

        assertThat(problem.keySet()).isSubsetOf(VALIDATION_PROBLEM_FIELDS);
        assertThat(problem).doesNotContainKey("errors");   // por eso es opcional en el contrato
        assertThat(problem).containsKey("timestamp");      // uniformidad: también lo recibe
        assertThat(problem).doesNotContainKey("instance");
    }

    /** Un error estándar (sesión ausente): solo campos de `ApiProblem`, sin `errors` ni `instance`. */
    @Test
    void standard_error_body_only_contains_documented_fields() throws Exception {
        String body = mockMvc.perform(get("/users/me"))
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();

        Map<String, Object> problem = errorBodyOf(body);

        assertThat(problem.keySet()).isSubsetOf(API_PROBLEM_FIELDS);
        assertThat(problem).containsKeys("type", "title", "status", "detail", "timestamp");
        assertThat(problem).doesNotContainKey("errors");
        assertThat(problem).doesNotContainKey("instance");
    }
}
