-- Capacidad: organization-branding (update-organization-branding) — reemplaza el color de marca
-- libre (hex) por un catálogo curado de presets pre-vetados para contraste AA. deriveTheme no
-- cambia; el backend valida la CLAVE del preset (este CHECK), el catálogo visual (hex + nombre)
-- vive en el frontend (design.md D2). No se conserva el shape viejo en paralelo: proyecto
-- pre-lanzamiento, sin datos de producción reales que preservar (design.md, sección Risks).

ALTER TABLE organization_branding
    DROP COLUMN brand_primary,
    DROP COLUMN brand_secondary,
    ADD COLUMN brand_preset VARCHAR(16)
        CHECK (brand_preset IN ('TEAL', 'VIOLET', 'INDIGO', 'EMERALD', 'AMBER', 'ROSE', 'SLATE'));
