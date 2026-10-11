package com.odontorisas.presentation;

import com.jayway.jsonpath.JsonPath;
import com.odontorisas.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>El guardián del contrato</b> (Scenario "Detección de drift entre el backend y el contrato").
 *
 * <p>Compara la especificación que el backend publica <b>ahora</b> con el snapshot versionado
 * {@code contracts/openapi.json}. Si alguien añade o cambia una operación y no regenera el
 * contrato, <b>esto rompe la build</b>.
 *
 * <p>Es la pieza que faltaba: el job {@code codegen-drift} del frontend solo vigilaba
 * <em>contrato → cliente</em>. Nadie vigilaba <em>backend → contrato</em>, así que el contrato
 * podía mentir impunemente (y de hecho mentía: decía 200 donde la API devuelve 201 y 204, y no
 * documentaba ni un error). Sin este guardián, arreglar el contrato hoy no serviría de nada:
 * volvería a desincronizarse con el primer endpoint de la próxima capacidad.
 *
 * <p><b>Por qué un test y no un job que arranque el servidor</b> (design.md, Open Question 2):
 * arrancar un servidor real y hacerle {@code curl} en CI añade piezas móviles y frágiles. Este
 * test usa la misma maquinaria de springdoc, corre dentro del {@code verify} que CI ya ejecuta y
 * rompe la build igual. Es la vía más estable.
 *
 * <p>La comparación es <b>semántica</b> (se parsea el JSON), no textual: el formato del snapshot
 * —indentado, orden de claves— es irrelevante.
 */
@SpringBootTest
// La documentación está apagada por defecto (update-api-docs-exposure, design D2): este test la enciende.
@TestPropertySource(properties = "SWAGGER_ENABLED=true")
@AutoConfigureMockMvc
class ContractDriftIT extends AbstractIntegrationTest {

    /** El test corre con el directorio de trabajo en {@code modules/backend}. */
    private static final Path VERSIONED_CONTRACT = Path.of("..", "..", "contracts", "openapi.json");

    private static final String REGENERATE =
        """
        El contrato versionado (contracts/openapi.json) NO refleja el backend actual.

        Has cambiado la API sin regenerar el contrato. El frontend deriva sus tipos de ese
        archivo, así que quedaría desincronizado. Para arreglarlo:

            1) arranca el backend con SWAGGER_ENABLED=true (en secrets.properties)
            2) curl http://localhost:8080/api/v3/api-docs > contracts/openapi.json
            3) pnpm --filter dentdesk-frontend generate:api
            4) commitea ambos
        """;

    @Autowired
    MockMvc mockMvc;

    @Test
    void the_versioned_contract_reflects_the_current_backend() throws Exception {
        String live = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(VERSIONED_CONTRACT)
            .as("No se encuentra contracts/openapi.json")
            .exists();
        String versioned = Files.readString(VERSIONED_CONTRACT);

        assertThat(comparable(live))
            .as(REGENERATE)
            .isEqualTo(comparable(versioned));
    }

    /**
     * Descarta {@code servers} antes de comparar: su URL la genera la herramienta a partir del
     * host y el puerto reales, así que difiere entre el servidor local y el contexto de test. No
     * describe la API —sino dónde está desplegada—, y el frontend usa su propia baseURL. Incluirlo
     * haría fallar al guardián por una diferencia que no es drift.
     */
    private static Map<String, Object> comparable(String openApiJson) {
        Map<String, Object> spec = JsonPath.read(openApiJson, "$");
        spec.remove("servers");
        return spec;
    }
}
