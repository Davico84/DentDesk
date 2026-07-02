package com.odontorisas.infra.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad base (scaffolding).
 *
 * <p>Placeholder: expone Actuator health/info de forma pública y, por ahora,
 * permite el resto de peticiones (aún no hay capacidades de negocio). La
 * capacidad {@code authentication} reemplazará esta configuración con JWT y
 * autorización por rol.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                // TODO(authentication): restringir el resto de rutas cuando exista auth.
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
