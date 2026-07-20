package com.odontorisas.presentation.dto;

import com.odontorisas.common.BrandPreset;
import com.odontorisas.service.organization.Branding;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Marca visual de la organización activa. Campos {@code null} = valores por defecto del
 * producto (el frontend deriva el tema; ausencia de personalización = tema del producto).
 *
 * <p>Se construye desde {@link Branding} (capa de servicio), <b>nunca desde la entidad JPA</b>:
 * un DTO que lee la entidad ata el contrato de la API al esquema de la base de datos, y un
 * renombrado de columna se convierte en un cambio de contrato.
 */
public record BrandingResponse(
    @Schema(description = "Preset de marca elegido del catálogo curado, o null = default del producto")
    BrandPreset brandPreset,

    @Schema(example = "https://cdn.example.com/clinica/logo.png", description = "URL https del logo, o null = logo del producto")
    String logoUrl) {

    public static BrandingResponse from(Branding branding) {
        return new BrandingResponse(branding.brandPreset(), branding.logoUrl());
    }
}
