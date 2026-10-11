package com.odontorisas.health;

import com.odontorisas.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenarios "Ping sin sesión", "Liveness independiente de la base" e "Info no es pública"
 * (add-cold-start-warmup). Un indicador de prueba en DOWN simula una base caída: el health general
 * cae a 503 y liveness sigue UP, porque su grupo no incluye otros indicadores.
 * {@code HEALTH_SHOW_DETAILS} se fija explícito: el test no depende del entorno.
 */
@SpringBootTest
@TestPropertySource(properties = "HEALTH_SHOW_DETAILS=always")
@AutoConfigureMockMvc
class HealthPingIT extends AbstractIntegrationTest {

    @TestConfiguration
    static class DownIndicator {
        @Bean
        HealthIndicator broken() {
            return () -> Health.down().withDetail("motivo", "simulado").build();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void liveness_is_public_up_and_never_reports_the_database() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components.db").doesNotExist())
            .andExpect(jsonPath("$.components.broken").doesNotExist());
    }

    @Test
    void general_health_goes_down_while_liveness_stays_up() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.components.db").exists());
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void info_requires_authentication() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }
}
