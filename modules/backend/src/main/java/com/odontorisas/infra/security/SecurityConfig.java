package com.odontorisas.infra.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;

/**
 * Seguridad de la capacidad {@code authentication}: API stateless con access
 * token JWT (Bearer). Rutas públicas: {@code /auth/**}, health y el contrato
 * OpenAPI; el resto requiere autenticación. El refresh token viaja en cookie
 * {@code HttpOnly} sobre {@code /auth} (CORS con credenciales). CSRF se
 * deshabilita: no hay sesión de servidor y la cookie de refresh se protege con
 * {@code SameSite=Lax} + método POST.
 */
@Configuration
@EnableConfigurationProperties({SecurityProperties.class, LoginLockoutProperties.class})
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   SecurityProperties props,
                                                   @Qualifier("handlerExceptionResolver")
                                                   HandlerExceptionResolver handlerExceptionResolver)
            throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource(props)))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PublicPaths.PATTERNS).permitAll()
                .anyRequest().authenticated())
            // El 401 de "sesión ausente" se delega al HandlerExceptionResolver de MVC en vez de
            // usar sendError(401) (que devolvía el cuerpo por defecto del contenedor). Así lo
            // renderiza el mismo GlobalExceptionHandler que el resto de errores y la API falla
            // SIEMPRE con la misma forma (RFC 9457) — requisito para que el contrato pueda
            // describir los errores con un único schema.
            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                (request, response, authException) ->
                    handlerExceptionResolver.resolveException(request, response, null, authException)))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource(SecurityProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
