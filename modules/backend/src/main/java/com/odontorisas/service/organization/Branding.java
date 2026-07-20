package com.odontorisas.service.organization;

import com.odontorisas.common.BrandPreset;
import com.odontorisas.persistence.entity.OrganizationBranding;

/**
 * Marca visual de una organización, en el lenguaje de la <b>capa de servicio</b>.
 *
 * <p>Existe para que {@link BrandingService} no hable DTOs de {@code presentation}: un servicio
 * que recibe y devuelve tipos de la capa web queda <b>atado al transporte</b> —no se puede
 * reutilizar desde otro punto de entrada, y un cambio de contrato HTTP se filtra hasta la lógica—.
 * La traducción a {@code BrandingResponse} es responsabilidad del controller, que es quien conoce
 * la API.
 *
 * <p>Campos {@code null} = sin personalización (el frontend aplica los valores por defecto del
 * producto). "Sin personalización" es <b>ausencia de fila</b>, no una fila con nulos.
 */
public record Branding(BrandPreset brandPreset, String logoUrl) {

    static Branding from(OrganizationBranding entity) {
        return new Branding(entity.getBrandPreset(), entity.getLogoUrl());
    }

    /** Sin personalización: equivalente a los valores por defecto del producto. */
    public static Branding empty() {
        return new Branding(null, null);
    }

    /** {@code true} si no hay nada que personalizar (la fila debe eliminarse). */
    boolean isEmpty() {
        return brandPreset == null && logoUrl == null;
    }
}
