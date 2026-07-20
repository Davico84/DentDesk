package com.odontorisas.common;

/**
 * Catálogo curado de marcas que una organización puede elegir para personalizar su color de
 * marca, como <b>conjunto cerrado</b> (update-organization-branding design.md D1).
 *
 * <p>El backend solo valida y persiste la <b>clave</b> — no conoce el hex ni el nombre visible de
 * cada preset. El catálogo visual (color(es) reales, nombre de presentación) vive en el frontend
 * ({@code modules/branding/presets.ts}), para poder afinarlo sin migración de base de datos
 * (design.md D2). Cada clave aquí está pre-vetada para contraste AA por el catálogo del frontend
 * (verificado en {@code presets.test.ts}, no en este backend).
 */
public enum BrandPreset {
    TEAL,
    VIOLET,
    INDIGO,
    EMERALD,
    AMBER,
    ROSE,
    SLATE
}
