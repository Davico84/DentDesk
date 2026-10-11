package com.odontorisas.infra.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario "Variables del servicio" (add-cloud-deployment D3): el blueprint de Render versionado en
 * la raíz del repo. Valores no secretos fijos; credenciales sin valor ({@code sync: false}).
 */
class RenderBlueprintTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> service() throws Exception {
        try (Reader reader = Files.newBufferedReader(Path.of("../../render.yaml"))) {
            Map<String, Object> blueprint = new Yaml().load(reader);
            return ((List<Map<String, Object>>) blueprint.get("services")).getFirst();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void declares_fixed_values_and_secret_placeholders() throws Exception {
        List<Map<String, Object>> envVars = (List<Map<String, Object>>) service().get("envVars");
        Map<String, Object> fixed = envVars.stream().filter(v -> v.containsKey("value"))
            .collect(Collectors.toMap(v -> (String) v.get("key"), v -> v.get("value")));
        List<String> secrets = envVars.stream().filter(v -> Boolean.FALSE.equals(v.get("sync")))
            .map(v -> (String) v.get("key")).toList();

        assertThat(fixed).containsEntry("HEALTH_SHOW_DETAILS", "never")
            .containsEntry("SWAGGER_ENABLED", "false")
            .containsEntry("COOKIE_SECURE", "true");
        assertThat(secrets).containsExactlyInAnyOrder("DB_URL", "DB_USERNAME", "DB_PASSWORD",
            "DB_ADMIN_USERNAME", "DB_ADMIN_PASSWORD", "JWT_SECRET", "CORS_ALLOWED_ORIGINS");
        // Ninguna credencial con valor en el repo.
        assertThat(fixed).doesNotContainKeys("DB_PASSWORD", "DB_ADMIN_PASSWORD", "JWT_SECRET");
    }

    @Test
    void deploys_main_with_the_liveness_health_check_under_api() throws Exception {
        Map<String, Object> service = service();
        assertThat(service).containsEntry("branch", "main")
            .containsEntry("runtime", "docker")
            .containsEntry("plan", "free")
            .containsEntry("autoDeployTrigger", "checksPass")
            .containsEntry("healthCheckPath", "/api/actuator/health/liveness");
    }
}
