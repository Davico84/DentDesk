-- Capacidad: add-clinical-history — catálogo CIE10-MINSA (design.md D6).
--
-- Catálogo de REFERENCIA global: sin organization_id ni RLS (no es dato de una clínica, es el
-- mismo para todas). Jerarquía de 4 niveles (capítulo → grupo → categoría → subcategoría) en una
-- sola tabla, navegable por parent_code. El JSON fuente no trae código propio para capítulo/grupo
-- (solo categoría/subcategoría lo tienen): el loader genera códigos sintéticos deterministas
-- (CAP01, GRP0101...) para esos dos niveles — ver Cie10CatalogLoader.

CREATE TABLE cie10_code (
    code            VARCHAR(20) PRIMARY KEY,
    descripcion     VARCHAR(500) NOT NULL,
    nivel           VARCHAR(20) NOT NULL CHECK (nivel IN ('CAPITULO', 'GRUPO', 'CATEGORIA', 'SUBCATEGORIA')),
    parent_code     VARCHAR(20) REFERENCES cie10_code (code)
);

-- Navegación jerárquica (buscar hijos de un padre) y ordenar la ruta capítulo→...→subcategoría.
CREATE INDEX idx_cie10_code_parent ON cie10_code (parent_code);

-- Búsqueda por texto (el buscador del diagnóstico). B-tree simple: el volumen (~15k filas) no
-- justifica full-text search en esta fase; si la búsqueda por substring pesa, se revisita.
CREATE INDEX idx_cie10_code_descripcion ON cie10_code (descripcion);
