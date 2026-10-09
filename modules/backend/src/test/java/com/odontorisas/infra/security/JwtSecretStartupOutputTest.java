package com.odontorisas.infra.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scenario "El error no revela el secreto" sobre la SALIDA REAL del arranque (log + informe de
 * los FailureAnalyzer de Spring Boot), no solo sobre la cadena de excepciones. Arranque mínimo:
 * {@code spring.config.location} y {@code spring.config.additional-location} apuntan a un recurso
 * inexistente (por argumento, que gana a cualquier variable de entorno), así que no se lee ni
 * application.yml, ni secrets.properties, ni una SPRING_CONFIG_ADDITIONAL_LOCATION externa.
 */
@ExtendWith(OutputCaptureExtension.class)
class JwtSecretStartupOutputTest {

    private static final String NOWHERE = "optional:classpath:/__sin_config__.yml";

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityProperties.class)
    @Import(TokenService.class)
    static class MinimalApp {
    }

    @Test
    void failed_startup_output_names_the_property_but_not_the_secret(CapturedOutput output) {
        String shortSecret = "marcador-unico-QZX-7c1e0b9a8f2d"; // 31 bytes
        assertThat(shortSecret).hasSize(31);

        SpringApplication app = new SpringApplication(MinimalApp.class);
        app.setWebApplicationType(WebApplicationType.NONE);

        assertThatThrownBy(() -> app.run(
            "--spring.config.location=" + NOWHERE,
            "--spring.config.additional-location=" + NOWHERE,
            "--app.security.jwt.secret=" + shortSecret,
            "--app.security.jwt.issuer=test",
            "--app.security.jwt.access-ttl=PT15M",
            "--app.security.jwt.refresh-ttl=P7D"));

        assertThat(output.getAll())
            .contains("app.security.jwt.secret")
            .doesNotContain(shortSecret);
    }
}
