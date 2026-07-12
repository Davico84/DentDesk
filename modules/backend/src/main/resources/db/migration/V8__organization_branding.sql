-- Capacidad: organization-branding — personalización visual por organización.
-- Metadato del tenant (1:1 con organizations, como organization_settings): SIN RLS,
-- se accede por el organization_id del contexto. Todos los campos nullable:
-- ausencia de fila (o de campo) = valores por defecto del producto.
-- El GRANT al rol de app lo cubre el ALTER DEFAULT PRIVILEGES de V6.

CREATE TABLE organization_branding (
    organization_id BIGINT PRIMARY KEY REFERENCES organizations (id) ON DELETE CASCADE,
    brand_primary   VARCHAR(9),      -- hex "#RRGGBB"
    brand_secondary VARCHAR(9),      -- hex "#RRGGBB" (opcional; si null se deriva del primario)
    logo_url        VARCHAR(2048)    -- solo https:// (validado en la aplicación)
);
