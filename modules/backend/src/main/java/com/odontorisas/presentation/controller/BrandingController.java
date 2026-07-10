package com.odontorisas.presentation.controller;

import com.odontorisas.presentation.dto.BrandingResponse;
import com.odontorisas.presentation.dto.UpdateBrandingRequest;
import com.odontorisas.service.organization.BrandingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Marca visual de la organización activa. La organización es siempre la del contexto de la
 * sesión (nunca de la petición). Lectura para cualquier usuario autenticado; edición solo
 * para el administrador de la organización.
 */
@RestController
@RequestMapping("/organizations/me/branding")
@Tag(name = "organization-branding", description = "Personalización visual por organización")
public class BrandingController {

    private final BrandingService brandingService;

    public BrandingController(BrandingService brandingService) {
        this.brandingService = brandingService;
    }

    @GetMapping
    public BrandingResponse get() {
        return brandingService.getForCurrentOrganization();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public BrandingResponse update(@Valid @RequestBody UpdateBrandingRequest request) {
        return brandingService.update(request);
    }
}
