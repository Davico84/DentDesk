package com.odontorisas.infra.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Scenarios de "Aviso de backend listo al arrancar" (project-foundation). */
@ExtendWith(OutputCaptureExtension.class)
class StartupReadyBannerTest {

    private static final String RULE = "=".repeat(60);

    // --- scenario: Aviso con la URL real ---

    @Test
    void banner_is_a_delimited_block_with_name_and_real_port() {
        List<String> lines = StartupReadyBanner.render("Dentdesk", 9091, "", true);

        assertThat(lines).hasSize(4);
        assertThat(lines.getFirst()).isEqualTo(RULE);
        assertThat(lines.getLast()).isEqualTo(RULE);
        assertThat(lines.get(1)).isEqualTo("  >> Dentdesk API lista en http://localhost:9091");
    }

    @Test
    void context_path_is_part_of_both_urls() {
        List<String> lines = StartupReadyBanner.render("App", 8080, "/api/", true);

        assertThat(lines.get(1)).endsWith("http://localhost:8080/api");
        assertThat(lines.get(2)).endsWith("http://localhost:8080/api/swagger-ui.html");
        assertThat(StartupReadyBanner.render("App", 8080, "api", false).get(1)).endsWith("http://localhost:8080/api");
        assertThat(StartupReadyBanner.render("App", 8080, "/", false).get(1)).endsWith("http://localhost:8080");
        assertThat(StartupReadyBanner.render("App", 8080, null, false).get(1)).endsWith("http://localhost:8080");
    }

    @Test
    void listener_uses_the_product_name_by_default_and_writes_a_single_log_entry(CapturedOutput output) {
        new StartupReadyBanner(new MockEnvironment().withProperty("local.server.port", "8080")).onReady();

        String out = output.getOut() + output.getErr();
        // Una sola entrada: la línea del aviso no lleva el prefijo del logger delante.
        assertThat(out).containsPattern("(?m)^  >> Dentdesk API lista en http://localhost:8080$");
        assertThat(out).containsPattern("(?m)^={60}$");
    }

    @Test
    void app_name_overrides_the_product_name(CapturedOutput output) {
        MockEnvironment env = new MockEnvironment()
            .withProperty("app.name", "Clinica Demo")
            .withProperty("local.server.port", "8080");

        new StartupReadyBanner(env).onReady();

        assertThat(output.getAll()).contains("Clinica Demo API lista en http://localhost:8080");
    }

    @Test
    void banner_is_plain_ascii_so_any_console_shows_it() {
        for (String line : StartupReadyBanner.render("App", 8080, "", false)) {
            assertThat(line.chars()).as(line).allMatch(c -> c < 128);
        }
    }

    // --- scenarios: Documentación de la API activa / desactivada ---

    @Test
    void swagger_enabled_shows_its_url(CapturedOutput output) {
        new StartupReadyBanner(new MockEnvironment()
            .withProperty("local.server.port", "8080")
            .withProperty("springdoc.swagger-ui.enabled", "true")
            .withProperty("springdoc.api-docs.enabled", "true")).onReady();

        assertThat(output.getAll()).contains("Swagger UI: http://localhost:8080/swagger-ui.html");
    }

    @Test
    void swagger_disabled_says_so(CapturedOutput output) {
        MockEnvironment env = new MockEnvironment()
            .withProperty("local.server.port", "8080")
            .withProperty("springdoc.swagger-ui.enabled", "false");

        new StartupReadyBanner(env).onReady();

        assertThat(output.getAll()).contains("Swagger UI: desactivado (SWAGGER_ENABLED=false)");
    }

    @Test
    void swagger_ui_without_the_api_docs_is_announced_as_disabled(CapturedOutput output) {
        // Override directo de springdoc (SPRINGDOC_API_DOCS_ENABLED=false): la UI no tendría qué cargar.
        new StartupReadyBanner(new MockEnvironment()
            .withProperty("local.server.port", "8080")
            .withProperty("springdoc.swagger-ui.enabled", "true")
            .withProperty("springdoc.api-docs.enabled", "false")).onReady();

        assertThat(output.getAll()).contains("Swagger UI: desactivado (SWAGGER_ENABLED=false)");
    }

    @Test
    void swagger_is_disabled_when_the_property_is_missing(CapturedOutput output) {
        new StartupReadyBanner(new MockEnvironment().withProperty("local.server.port", "8080")).onReady();

        assertThat(output.getAll()).contains("Swagger UI: desactivado (SWAGGER_ENABLED=false)");
    }

    // --- scenario: Sin servidor web no hay aviso ---

    @Test
    void listener_writes_nothing_without_a_web_server(CapturedOutput output) {
        new StartupReadyBanner(new MockEnvironment()).onReady();

        assertThat(output.getAll()).doesNotContain("API lista en");
    }

    // --- scenario: Sin secretos en el aviso ---

    @Test
    void banner_never_contains_secrets(CapturedOutput output) {
        String jwt = "jwt-secreto-que-no-debe-salir-0123456789-abcdef";
        String dbPassword = "clave-bd-que-no-debe-salir-QZX";
        MockEnvironment env = new MockEnvironment()
            .withProperty("local.server.port", "8080")
            .withProperty("app.security.jwt.secret", jwt)
            .withProperty("spring.datasource.password", dbPassword);

        new StartupReadyBanner(env).onReady();

        assertThat(output.getAll()).contains("API lista en").doesNotContain(jwt).doesNotContain(dbPassword);
    }
}
