package com.odontorisas.service.organization;

import com.odontorisas.common.OrganizationContext;
import com.odontorisas.persistence.entity.OrganizationBranding;
import com.odontorisas.persistence.repository.OrganizationBrandingRepository;
import com.odontorisas.persistence.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marca visual de la organización activa (la del contexto de la sesión, nunca de la petición).
 * {@code organization_branding} es metadato del tenant (sin RLS): se accede por el
 * {@code organizationId} del contexto. "Sin personalización" = ausencia de fila; si una
 * actualización deja todos los campos vacíos, se elimina la fila (design.md D6).
 */
@Service
public class BrandingService {

    private final OrganizationBrandingRepository brandingRepository;
    private final OrganizationRepository organizationRepository;

    public BrandingService(OrganizationBrandingRepository brandingRepository,
                           OrganizationRepository organizationRepository) {
        this.brandingRepository = brandingRepository;
        this.organizationRepository = organizationRepository;
    }

    /** Marca de la organización activa; devuelve siempre un objeto (vacío = defaults del producto). */
    @Transactional(readOnly = true)
    public Branding getForCurrentOrganization() {
        return brandingRepository.findById(currentOrganizationId())
            .map(Branding::from)
            .orElseGet(Branding::empty);
    }

    /** Crea/actualiza la marca de la organización activa; si queda toda vacía, elimina la fila. */
    @Transactional
    public Branding update(Branding requested) {
        Long organizationId = currentOrganizationId();

        if (requested.isEmpty()) {
            brandingRepository.deleteById(organizationId); // idempotente si no existe
            return Branding.empty();
        }

        OrganizationBranding branding = brandingRepository.findById(organizationId)
            .orElseGet(() -> OrganizationBranding.builder()
                .organization(organizationRepository.getReferenceById(organizationId))
                .build());
        branding.setBrandPrimary(requested.brandPrimary());
        branding.setBrandSecondary(requested.brandSecondary());
        branding.setLogoUrl(requested.logoUrl());
        return Branding.from(brandingRepository.save(branding));
    }

    private Long currentOrganizationId() {
        Long organizationId = OrganizationContext.get();
        if (organizationId == null) {
            throw new IllegalStateException("No hay organización en el contexto de la petición.");
        }
        return organizationId;
    }
}
