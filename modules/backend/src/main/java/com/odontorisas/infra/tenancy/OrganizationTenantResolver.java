package com.odontorisas.infra.tenancy;

import com.odontorisas.common.OrganizationContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Resuelve el tenant (organización) que Hibernate usa para filtrar/poblar las entidades
 * anotadas con {@code @TenantId}. Lee de {@link OrganizationContext} (poblado desde el
 * token firmado).
 *
 * <p>Hibernate exige un identificador no nulo. Cuando no hay organización en contexto
 * (arranque, Flyway, flujos de sistema) se devuelve el centinela {@link #NO_ORGANIZATION}:
 * a nivel de aplicación no casa con ninguna fila. El fallo <em>ruidoso</em> ante una
 * consulta de negocio sin contexto lo aporta la Row-Level Security del motor (la variable
 * {@code app.organization_id} no fijada hace que {@code current_setting} lance).
 */
@Component
public class OrganizationTenantResolver implements CurrentTenantIdentifierResolver<Long> {

    /** Centinela para cuando no hay organización en contexto (no casa con ninguna fila). */
    public static final Long NO_ORGANIZATION = 0L;

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long organizationId = OrganizationContext.get();
        return organizationId != null ? organizationId : NO_ORGANIZATION;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
