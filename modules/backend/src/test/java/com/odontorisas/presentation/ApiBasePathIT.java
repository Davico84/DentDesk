package com.odontorisas.presentation;

import com.odontorisas.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La API bajo la base {@code /api} con un servidor real (add-cloud-deployment D1). MockMvc no aplica
 * el {@code server.servlet.context-path}, así que el resto de los tests no prueba el prefijo: este sí,
 * por HTTP de verdad. Scenarios de {@code cloud-deployment}: "Endpoints bajo /api", "Preflight CORS a
 * la API" y "Cookie de refresh acotada a la base".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "CORS_ALLOWED_ORIGINS=https://app.example.test")
class ApiBasePathIT extends AbstractIntegrationTest {

    private static final String ORIGIN = "https://app.example.test";

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> postJson(String path, String body, String cookie) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            req.header("Cookie", cookie);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void liveness_lives_under_the_api_base() throws Exception {
        HttpResponse<String> res = http.send(
            HttpRequest.newBuilder(uri("/api/actuator/health/liveness")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void routes_without_the_base_are_not_the_api() throws Exception {
        HttpResponse<String> res = http.send(
            HttpRequest.newBuilder(uri("/patients")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        // Fuera del context-path no hay aplicación: el contenedor responde 404, no la API.
        assertThat(res.statusCode()).isEqualTo(404);
        assertThat(res.body()).doesNotContain("\"content\"");
    }

    @Test
    void refresh_cookie_is_scoped_to_api_auth_and_travels_to_refresh() throws Exception {
        String email = "base-" + UUID.randomUUID() + "@clinica.test";
        HttpResponse<String> registered = postJson("/api/auth/register",
            invitedAdminRegisterBody("Clínica Base", email, "password123", "Ana Base"), null);
        assertThat(registered.statusCode()).isEqualTo(201);

        List<String> setCookies = registered.headers().allValues("Set-Cookie");
        String refreshCookie = setCookies.stream().filter(c -> c.startsWith("refresh_token=")).findFirst().orElseThrow();
        assertThat(refreshCookie).contains("Path=/api/auth").contains("HttpOnly").contains("SameSite=Lax");

        // El navegador la enviaría a /api/auth/refresh porque su path coincide: aquí se reenvía igual.
        String cookie = refreshCookie.substring(0, refreshCookie.indexOf(';'));
        HttpResponse<String> refreshed = postJson("/api/auth/refresh", "", cookie);
        assertThat(refreshed.statusCode()).isEqualTo(200);
        assertThat(refreshed.body()).contains("accessToken");
    }

    @Test
    void cors_preflight_to_the_api_is_allowed_for_the_configured_origin() throws Exception {
        HttpResponse<String> res = http.send(
            HttpRequest.newBuilder(uri("/api/auth/refresh"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isBetween(200, 204);
        assertThat(res.headers().firstValue("Access-Control-Allow-Origin")).hasValue(ORIGIN);
        assertThat(res.headers().firstValue("Access-Control-Allow-Credentials")).hasValue("true");
    }
}
