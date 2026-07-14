package com.odontorisas.presentation.dto;

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
    @Schema(example = "#7f1aa6", description = "Color primario de marca (hex), o null = default")
    String brandPrimary,

    @Schema(example = "#16C3B2", description = "Color secundario (hex), o null = derivado del primario")
    String brandSecondary,

    @Schema(example = "https://cdn.example.com/clinica/logo.png", description = "URL https del logo, o null = logo del producto")
    String logoUrl) {

    public static BrandingResponse from(Branding branding) {
        return new BrandingResponse(branding.brandPrimary(), branding.brandSecondary(), branding.logoUrl());
    }
}
