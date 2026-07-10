package com.odontorisas.presentation.dto;

import com.odontorisas.persistence.entity.OrganizationBranding;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Marca visual de la organización activa. Campos {@code null} = valores por defecto del
 * producto (el frontend deriva el tema; ausencia de personalización = tema del producto).
 */
public record BrandingResponse(
    @Schema(example = "#7f1aa6", description = "Color primario de marca (hex), o null = default")
    String brandPrimary,

    @Schema(example = "#16C3B2", description = "Color secundario (hex), o null = derivado del primario")
    String brandSecondary,

    @Schema(example = "https://cdn.example.com/clinica/logo.png", description = "URL https del logo, o null = logo del producto")
    String logoUrl) {

    public static BrandingResponse from(OrganizationBranding branding) {
        return new BrandingResponse(branding.getBrandPrimary(), branding.getBrandSecondary(), branding.getLogoUrl());
    }

    /** Sin personalización: todos los campos vacíos (equivalente a los valores por defecto). */
    public static BrandingResponse empty() {
        return new BrandingResponse(null, null, null);
    }
}
