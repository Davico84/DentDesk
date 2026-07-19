package com.odontorisas.infra.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enciende Spring Data JPA Auditing ({@code @CreatedBy}/{@code @LastModifiedBy}) usando
 * {@link com.odontorisas.infra.tenancy.IdentityAuditorAware} como fuente del usuario actual.
 * Separada de la lógica del {@code AuditorAware} a propósito (mismo patrón que
 * {@code TenantAwareTransactionManager}, un {@code @Component} simple sin anotaciones de
 * "encendido" mezcladas): esta clase deja explícito, en un solo sitio, que el mecanismo está
 * activo — ver {@code docs/backend.md §8.6}.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "identityAuditorAware")
public class JpaAuditingConfig {
}
