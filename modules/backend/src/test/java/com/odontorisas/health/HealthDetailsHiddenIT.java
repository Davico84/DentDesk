package com.odontorisas.health;

import com.odontorisas.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Scenario "Detalle oculto en despliegue" (add-cold-start-warmup D2). */
@SpringBootTest
@TestPropertySource(properties = "HEALTH_SHOW_DETAILS=never")
@AutoConfigureMockMvc
class HealthDetailsHiddenIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void health_only_reports_status() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist());
    }
}
