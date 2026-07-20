# organization-branding Specification

## Purpose

Personaliza la marca visual de cada organización (color de marca y logo) y la aplica al **app shell** una vez establecida la sesión. Parte de una marca de producto por defecto: una organización que no personaliza nada usa esos valores sin requerir configuración. El tema se **deriva** del color de marca (color primario, superficies y foco) garantizando contraste legible (WCAG AA) y preservando el modo claro/oscuro; los colores **semánticos** (éxito, aviso, error) no cambian. La edición está restringida al `ADMIN` de la organización activa (contexto de sesión, nunca indicada por el cliente); la marca solo se aplica tras el login (las pantallas de auth conservan la marca del producto) y el favicon es siempre el del producto. El logo es una URL externa (`https`) con degradación elegante al logo del producto si no está disponible. Depende de `multi-tenancy`.

## Requirements

### Requirement: Tema de la organización aplicado en el app shell
El sistema SHALL aplicar la marca visual de la organización activa (color de marca y logo) al app shell una vez establecida la sesión. Una organización que no ha personalizado su marca SHALL usar los valores por defecto del producto, sin requerir configuración previa.

#### Scenario: Organización con color de marca
- **WHEN** un usuario inicia sesión y su organización activa tiene un color de marca definido
- **THEN** el app shell se presenta con el tema derivado de ese color (color primario, superficies y foco)
- **AND** el logo de la organización se muestra en su cabecera

#### Scenario: Organización sin personalizar
- **WHEN** un usuario inicia sesión y su organización activa no ha definido marca
- **THEN** el app shell usa los colores y el logo por defecto del producto
- **AND** no se requiere ninguna fila de configuración para ello

#### Scenario: La marca cambia sin recargar
- **WHEN** el administrador guarda un nuevo color de marca
- **THEN** el app shell refleja el nuevo tema sin necesidad de recargar la página

#### Scenario: Cambiar de organización activa cambia el tema
- **WHEN** un usuario con acceso a varias organizaciones cambia su organización activa
- **THEN** el app shell se re-tematiza con la marca de la nueva organización
- **AND** no conserva ningún rastro visual de la organización anterior

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

### Requirement: El modo oscuro se preserva con la marca
El sistema SHALL mantener el funcionamiento del modo claro/oscuro con la marca de la organización aplicada. Los colores **semánticos** (éxito, aviso, error) NO SHALL cambiar con la marca.

#### Scenario: Alternar a modo oscuro con marca aplicada
- **WHEN** un usuario con una organización personalizada alterna al modo oscuro
- **THEN** el tema oscuro se presenta con la marca de la organización y contraste legible

#### Scenario: Los estados semánticos no cambian con la marca
- **WHEN** una organización define un color de marca
- **THEN** los colores de éxito, aviso y error se mantienen en sus tonos semánticos (no adoptan el color de marca)

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

### Requirement: La marca solo se aplica tras el login
El sistema SHALL aplicar la marca de la organización únicamente en el app shell (tras autenticarse). Las pantallas de autenticación (login y registro) SHALL conservar la marca del producto, porque antes de autenticar no se conoce la organización.

#### Scenario: Pantallas de auth con marca del producto
- **WHEN** un visitante sin sesión abre las pantallas de login o registro
- **THEN** se presentan con la marca del producto (no la de ninguna organización)

#### Scenario: Reset al cerrar sesión
- **WHEN** un usuario con una organización personalizada cierra sesión
- **THEN** el tema vuelve al del producto

#### Scenario: El favicon es el del producto siempre
- **WHEN** un usuario autenticado navega por el app shell de cualquier organización
- **THEN** la pestaña del navegador muestra el favicon del producto
- **AND** ninguna organización puede cambiarlo

### Requirement: El logo externo siempre tiene respaldo
El logo es una URL a un recurso alojado fuera del sistema, cuya disponibilidad no se puede garantizar (la organización puede borrar la imagen o su host puede fallar). El sistema SHALL degradar con elegancia al logo por defecto del producto, sin mostrar nunca un recurso roto.

#### Scenario: El logo de la organización no está disponible
- **WHEN** la organización tiene un logo definido pero la URL no devuelve una imagen válida
- **THEN** el app shell muestra el logo del producto en su lugar
- **AND** no se presenta ninguna imagen rota
