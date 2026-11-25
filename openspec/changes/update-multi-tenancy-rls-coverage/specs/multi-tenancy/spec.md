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
Toda tabla clasificada como aislada por organización o por identidad SHALL tener Row-Level Security habilitada, forzada y al menos una política en el catálogo; las aisladas por organización MUST además tener la columna `organization_id`. Es una garantía estructural: que la política aísle bien lo prueban los tests de aislamiento de cada capacidad.

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

### Requirement: Toda función que salta la RLS se declara
Toda función o procedimiento `SECURITY DEFINER` de `public` que no pertenezca a una extensión SHALL figurar, por su firma, en una lista declarada con su motivo y los tests que lo cubren, y MUST fijar `search_path` a un valor permitido. La build MUST fallar ante una función sin declarar, una entrada obsoleta o un `search_path` ausente o no permitido.

#### Scenario: Función SECURITY DEFINER sin declarar
- **WHEN** una migración crea una función `SECURITY DEFINER` que no figura en la lista
- **THEN** la prueba de inventario falla y nombra la función

#### Scenario: Función sin search_path fijo
- **WHEN** una función declarada es `SECURITY DEFINER` y no fija `search_path`, o lo fija a un valor no permitido
- **THEN** la prueba de inventario falla y nombra la función

#### Scenario: Sobrecarga sin declarar
- **WHEN** una migración crea una sobrecarga `SECURITY DEFINER` de una función declarada, con otros argumentos
- **THEN** la prueba de inventario falla y nombra la firma nueva

#### Scenario: Funciones de extensiones ignoradas
- **WHEN** una extensión instala funciones en `public`
- **THEN** la prueba de inventario no las exige en la lista

### Requirement: Nada queda fuera del inventario sin decidirse
La prueba de inventario MUST fallar si en `public` existe una vista o vista materializada, si existe una tabla, vista o función en un esquema de usuario distinto de `public`, o si el rol con el que lee tiene `SUPERUSER` o `BYPASSRLS`, es dueño de una tabla de `public`, o puede asumir con `SET ROLE` un rol con esos atributos o al dueño. Cada uno de esos casos SHALL decidirse en su propio cambio.

#### Scenario: Vista nueva
- **WHEN** una migración crea una vista en `public`
- **THEN** la prueba de inventario falla y nombra la vista

#### Scenario: Objeto en otro esquema
- **WHEN** una migración crea una tabla en un esquema distinto de `public`
- **THEN** la prueba de inventario falla y nombra el esquema y la tabla

#### Scenario: Rol que salta la RLS
- **WHEN** el rol con el que lee la prueba tiene `BYPASSRLS`, o es miembro con opción `SET` de un rol que lo tiene
- **THEN** la prueba de inventario falla en vez de clasificar

#### Scenario: Rol que puede asumir al dueño de las tablas
- **WHEN** el rol con el que lee es miembro con opción `SET` del rol dueño de las tablas de `public`
- **THEN** la prueba de inventario falla

#### Scenario: Membresía solo con INHERIT
- **WHEN** el rol con el que lee es miembro sin opción `SET` de un rol con `BYPASSRLS`
- **THEN** esa membresía no hace fallar la prueba

### Requirement: El inventario se verifica como rol de aplicación y se prueba fallando
La prueba de inventario SHALL leer el catálogo de PostgreSQL conectada como el rol de aplicación no superusuario, sin depender de vistas filtradas por privilegios, y su lógica de clasificación MUST tener pruebas con instantáneas sintéticas que demuestren que cada violación hace fallar la regla.

#### Scenario: Catálogo vacío no pasa como verde
- **WHEN** la lectura del catálogo no devuelve las tablas esperadas (p. ej. `patients` e `invitations`)
- **THEN** la prueba de inventario falla en vez de pasar vacía

#### Scenario: Cada violación se detecta
- **WHEN** la lógica de clasificación recibe una instantánea con una violación deliberada de cada regla
- **THEN** devuelve una violación que nombra la tabla y la regla incumplida
