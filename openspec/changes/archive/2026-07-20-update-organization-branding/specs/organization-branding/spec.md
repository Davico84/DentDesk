## MODIFIED Requirements

### Requirement: Edición de la marca restringida al administrador
El sistema SHALL permitir consultar la marca de la organización activa a cualquier usuario autenticado, y **actualizarla solo al administrador** de esa organización. La organización sobre la que se opera SHALL ser siempre la del contexto de la sesión, nunca una indicada por el cliente. El color de marca SHALL elegirse de un **catálogo curado de presets** pre-aprobados, no de un valor hex libre.

#### Scenario: El administrador actualiza la marca
- **WHEN** un usuario con rol `ADMIN` elige un preset de marca del catálogo y guarda un logo válido
- **THEN** el sistema persiste la marca de su organización
- **AND** la nueva marca queda disponible para aplicarse

#### Scenario: Un no administrador intenta actualizar la marca
- **WHEN** un usuario sin rol `ADMIN` intenta actualizar la marca de su organización
- **THEN** el sistema deniega la operación
- **AND** no modifica la marca

#### Scenario: Valores de entrada inválidos
- **WHEN** el administrador envía una clave de preset que no existe en el catálogo, o una URL de logo malformada
- **THEN** el sistema responde con un error de validación
- **AND** no persiste ningún cambio

#### Scenario: URL de logo con esquema no seguro
- **WHEN** el administrador envía como URL de logo un valor con un esquema distinto de `https` (por ejemplo `javascript:`, `data:` o `http:`)
- **THEN** el sistema rechaza la petición con un error de validación
- **AND** no persiste ninguna URL

#### Scenario: Restaurar el color al valor por defecto
- **WHEN** el administrador quita el preset de marca elegido (vuelve a "ninguno") y guarda
- **THEN** el sistema elimina esa personalización de color
- **AND** el app shell vuelve a usar el color por defecto del producto

#### Scenario: Quitar toda la personalización
- **WHEN** el administrador quita el preset de marca y el logo, y guarda
- **THEN** el sistema deja de almacenar personalización alguna para esa organización
- **AND** consultar la marca devuelve todos los campos vacíos (equivalente a los valores por defecto)

### Requirement: Legibilidad garantizada (contraste)
El sistema SHALL garantizar que cada preset del catálogo de marca cumple contraste legible (WCAG AA) para el texto sobre ese color **antes** de estar disponible para elegir — la legibilidad se garantiza por construcción del catálogo, no por una advertencia reactiva ante cualquier color. El sistema SHALL además elegir los colores de texto sobre el color de marca (`-foreground`) de modo que se mantenga el contraste, como defensa adicional; el sistema NUNCA SHALL presentar texto ilegible por diseño.

#### Scenario: Color de marca oscuro
- **WHEN** una organización usa un preset de marca oscuro
- **THEN** el texto sobre ese color se presenta en un tono claro que mantiene el contraste legible

#### Scenario: Color de marca claro
- **WHEN** una organización usa un preset de marca claro
- **THEN** el texto sobre ese color se presenta en un tono oscuro que mantiene el contraste legible

#### Scenario: Ningún preset del catálogo tiene contraste insuficiente
- **WHEN** se lista el catálogo de presets disponibles para elegir
- **THEN** todos los presets ya cumplen el contraste mínimo AA
- **AND** el administrador no puede seleccionar un preset con contraste insuficiente porque ninguno existe en el catálogo
