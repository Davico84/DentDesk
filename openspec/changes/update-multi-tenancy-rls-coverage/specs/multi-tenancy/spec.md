## ADDED Requirements

### Requirement: Toda tabla del esquema está clasificada por su aislamiento
Toda tabla del esquema `public` SHALL figurar en exactamente una lista declarada de clasificación (por organización, por identidad, excepción por clínica, o sin organización), y la build MUST fallar ante una tabla sin clasificar o una entrada que ya no corresponda a ninguna tabla.

#### Scenario: Tabla nueva sin clasificar
- **WHEN** una migración crea una tabla que no figura en ninguna lista
- **THEN** la prueba de inventario falla y nombra la tabla

#### Scenario: Entrada obsoleta
- **WHEN** una lista nombra una tabla que ya no existe en el esquema
- **THEN** la prueba de inventario falla y nombra la entrada

#### Scenario: Tabla en dos listas
- **WHEN** una misma tabla figura en dos listas
- **THEN** la prueba de inventario falla y nombra la tabla

### Requirement: Las tablas aisladas por el motor tienen RLS forzada con política
Toda tabla clasificada como aislada por organización o por identidad SHALL tener Row-Level Security habilitada, forzada y al menos una política; las aisladas por organización MUST además tener la columna `organization_id`.

#### Scenario: Tabla aislada sin RLS forzada
- **WHEN** una tabla aislada por organización tiene RLS habilitada pero no forzada
- **THEN** la prueba de inventario falla y nombra la tabla

#### Scenario: Tabla aislada sin política
- **WHEN** una tabla aislada por identidad tiene RLS forzada y ninguna política
- **THEN** la prueba de inventario falla y nombra la tabla

#### Scenario: Cobertura completa
- **WHEN** todas las tablas aisladas cumplen RLS habilitada, forzada y con política
- **THEN** la prueba de inventario pasa

### Requirement: Toda excepción por clínica se declara con motivo y decisión
Una tabla con columna `organization_id` y sin RLS SHALL figurar como excepción por clínica con su motivo y el ADR que la decide, y NO SHALL figurar en la lista de tablas sin organización. Si una excepción gana RLS, la lista MUST actualizarse en el mismo cambio.

#### Scenario: Tabla con organization_id sin RLS y sin excepción
- **WHEN** una tabla con `organization_id` carece de RLS y no figura como excepción
- **THEN** la prueba de inventario falla y nombra la tabla

#### Scenario: Excepción que ganó RLS
- **WHEN** una tabla declarada como excepción pasa a tener RLS forzada con política
- **THEN** la prueba de inventario falla pidiendo moverla a la lista de aisladas

#### Scenario: Tabla sin organización que la adquiere
- **WHEN** una tabla de la lista sin organización gana una columna `organization_id`
- **THEN** la prueba de inventario falla y nombra la tabla

### Requirement: El inventario se verifica como rol de aplicación y se prueba fallando
La prueba de inventario SHALL leer el catálogo de PostgreSQL conectada como el rol de aplicación no superusuario, sin depender de vistas filtradas por privilegios, y su lógica de clasificación MUST tener pruebas con instantáneas sintéticas que demuestren que cada violación hace fallar la regla.

#### Scenario: Catálogo vacío no pasa como verde
- **WHEN** la lectura del catálogo no devuelve las tablas esperadas (p. ej. `patients` e `invitations`)
- **THEN** la prueba de inventario falla en vez de pasar vacía

#### Scenario: Cada violación se detecta
- **WHEN** la lógica de clasificación recibe una instantánea con una violación deliberada de cada regla
- **THEN** devuelve una violación que nombra la tabla y la regla incumplida
