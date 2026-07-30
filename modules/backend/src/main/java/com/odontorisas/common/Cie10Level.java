package com.odontorisas.common;

/**
 * Nivel de un código dentro de la jerarquía CIE10-MINSA (design.md D6, `add-clinical-history`):
 * capítulo → grupo → categoría → subcategoría. Capítulo y grupo usan códigos sintéticos
 * ({@code Cie10CatalogLoader}); categoría y subcategoría, su código CIE10 real.
 */
public enum Cie10Level {
    CAPITULO,
    GRUPO,
    CATEGORIA,
    SUBCATEGORIA
}
