package com.odontorisas.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valores por defecto de la configuración versionada (add-cold-start-warmup): se lee
 * {@code application.yml} tal cual, sin resolver placeholders, así el test no depende del
 * entorno ni de un {@code secrets.properties} local (mismo patrón que {@code ApiDocsDefaultTest}).
 */
class ActuatorDefaultsTest {

    private static Object raw(String key) throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"));
        return sources.stream().map(s -> s.getProperty(key)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    /** Scenario "Detalle visible en local": sin la variable, el detalle sigue como hasta ahora. */
    @Test
    void health_details_default_to_always_and_probes_are_on() throws Exception {
        assertThat(raw("management.endpoint.health.show-details")).hasToString("${HEALTH_SHOW_DETAILS:always}");
        assertThat(raw("management.endpoint.health.probes.enabled")).hasToString("true");
    }

    /** Scenario "Configuración por defecto del pool". */
    @Test
    void pool_defaults_let_the_database_sleep() throws Exception {
        Map<String, String> expected = Map.of(
            "spring.datasource.hikari.minimum-idle", "${DB_POOL_MIN_IDLE:0}",
            "spring.datasource.hikari.idle-timeout", "${DB_POOL_IDLE_TIMEOUT_MS:60000}",
            "spring.datasource.hikari.keepalive-time", "0",
            "spring.datasource.hikari.maximum-pool-size", "${DB_POOL_MAX_SIZE:5}");
        for (var e : expected.entrySet()) {
            assertThat(raw(e.getKey())).as(e.getKey()).hasToString(e.getValue());
        }
    }
}
