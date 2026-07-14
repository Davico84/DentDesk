package com.odontorisas.presentation.controller;

import com.odontorisas.presentation.dto.BrandingResponse;
import com.odontorisas.presentation.dto.UpdateBrandingRequest;
import com.odontorisas.presentation.dto.ValidationProblem;
import com.odontorisas.service.organization.Branding;
import com.odontorisas.service.organization.BrandingService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    /**
     * Deliberadamente <b>no declara ninguna respuesta de error</b>: el {@code 401} y el {@code 500}
     * los inyecta el customizer de OpenAPI a partir de la ruta. Es la demostración de que los
     * errores transversales se heredan <b>sin escribir una línea</b> (lo verifica
     * {@code OpenApiContractIT}), que es como los heredarán las capacidades futuras.
     */
    @GetMapping
    public BrandingResponse getBranding() {
        return BrandingResponse.from(brandingService.getForCurrentOrganization());
    }

    /**
     * El {@code 401}, el {@code 403} (por {@code @PreAuthorize}) y el {@code 500} los pone el
     * customizer. El {@code 200} hay que declararlo: al declarar el {@code 400}, la herramienta
     * <b>descarta la respuesta de éxito que infería</b>, y el contrato perdería el schema de
     * {@link BrandingResponse}.
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Marca actualizada (o eliminada, si se envía vacía).",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = BrandingResponse.class))),
        @ApiResponse(responseCode = "400", description = "Color con formato inválido, o URL de logo que no es https.",
            content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ValidationProblem.class)))
    })
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public BrandingResponse updateBranding(@Valid @RequestBody UpdateBrandingRequest request) {
        // La traducción entre el DTO de la API y el tipo del servicio vive AQUÍ: el controller es
        // quien conoce el contrato HTTP. El servicio no debe saber que existe una API REST.
        Branding requested = new Branding(request.brandPrimary(), request.brandSecondary(), request.logoUrl());
        return BrandingResponse.from(brandingService.update(requested));
    }
}
