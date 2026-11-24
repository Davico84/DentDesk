## ADDED Requirements

### Requirement: Dependencias del frontend entre módulos declaradas
El frontend SHALL declarar en un único mapa de configuración las dependencias permitidas entre `modules/*`, y el lint MUST rechazar cualquier import de un módulo hacia otro módulo de negocio que no figure en ese mapa. Los archivos de prueba (`*.test.*`, `*.spec.*` y carpetas `test/`) quedan exentos. La regla cubre imports estáticos con alias `@/modules/...`.

#### Scenario: Import entre módulos declarado
- **WHEN** `modules/clinical-history` importa de `@/modules/staff`, y el mapa lo declara
- **THEN** `pnpm lint` no reporta error

#### Scenario: Import entre módulos no declarado
- **WHEN** `modules/patients` importa de `@/modules/staff` y el mapa no lo declara
- **THEN** `pnpm lint` falla con un error de `no-restricted-imports` que nombra al módulo importado

#### Scenario: Archivo de prueba exento
- **WHEN** un `*.test.tsx` de `modules/patients` importa de `@/modules/auth`
- **THEN** `pnpm lint` no reporta error

### Requirement: Core independiente de los módulos de negocio
`modules/core` SHALL NOT importar de ningún otro módulo bajo `modules/`, y el lint MUST rechazarlo en el código de producción (los archivos de prueba quedan exentos, igual que arriba). Esto hace cumplir por regla lo que la estructura del frontend ya exige.

#### Scenario: Core importa de un módulo de negocio
- **WHEN** un archivo de `modules/core` importa de `@/modules/patients`
- **THEN** `pnpm lint` falla con un error de `no-restricted-imports`

#### Scenario: Core importa de sí mismo
- **WHEN** un archivo de `modules/core` importa de `@/modules/core/ui`
- **THEN** `pnpm lint` no reporta error

### Requirement: Servicios del backend sin ciclos entre dominios
Los paquetes `service.<dominio>` del backend SHALL NOT formar ciclos de dependencia entre sí, y la build MUST fallar si aparece uno.

#### Scenario: Grafo sin ciclos
- **WHEN** se ejecuta la regla de ciclos sobre los paquetes `service.<dominio>`
- **THEN** la regla pasa
- **AND** `service.auth` depende de `service.staff` pero `service.staff` no depende de `service.auth`

#### Scenario: Ciclo introducido
- **WHEN** una clase de `service.staff` importa una clase de `service.auth`
- **THEN** la regla de ciclos falla y nombra ambos paquetes

### Requirement: Dependencias del backend entre dominios declaradas
Cada paquete `service.<dominio>` SHALL depender únicamente de los dominios que su entrada del mapa de dependencias permitidas declara, y la build MUST fallar ante una dependencia no declarada.

#### Scenario: Dependencia declarada
- **WHEN** `service.consultationnote` depende de `service.clinicalhistory`, y el mapa lo declara
- **THEN** la regla pasa

#### Scenario: Dependencia no declarada
- **WHEN** `service.patient` depende de `service.staff` y el mapa no lo declara
- **THEN** la regla falla y nombra ambos paquetes

#### Scenario: Dominio sin entrada en el mapa
- **WHEN** existe un paquete `service.<dominio>` sin entrada en el mapa
- **THEN** la regla falla pidiendo declararlo

### Requirement: Reglas de arquitectura comprobadas contra violación
Cada regla de límites entre módulos SHALL tener una prueba que introduce una violación deliberada y verifica que la regla falla. Las reglas del backend MUST comprobar además que el importador de clases no está vacío.

#### Scenario: La regla falla ante la violación
- **WHEN** la prueba de la regla del frontend lintea un archivo `modules/patients/**` que importa de un módulo no declarado
- **THEN** el resultado contiene un error de la regla

#### Scenario: Importador vacío no pasa como verde
- **WHEN** el importador de ArchUnit no lee ninguna clase de `service`
- **THEN** la prueba de las reglas del backend falla en vez de pasar vacía
