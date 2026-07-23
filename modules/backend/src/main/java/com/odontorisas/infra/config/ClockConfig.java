package com.odontorisas.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Expone el reloj del sistema como bean inyectable (design.md D11, update-authentication):
 * el cálculo/comparación de {@code locked_until} lo usa en vez de {@code Instant.now()} directo,
 * para que los tests de expiración avancen el reloj en vez de dormir.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
