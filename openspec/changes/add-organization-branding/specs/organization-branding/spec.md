## ADDED Requirements

### Requirement: Tema de la organización aplicado en el app shell
El sistema SHALL aplicar la marca visual de la organización activa (color de marca, logo, favicon) al app shell una vez establecida la sesión. Una organización que no ha personalizado su marca SHALL usar los valores por defecto del producto, sin requerir configuración previa.

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

### Requirement: Legibilidad garantizada (contraste)
El sistema SHALL elegir los colores de texto sobre el color de marca (`-foreground`) de modo que se mantenga un contraste legible (WCAG AA), con independencia del color elegido. El sistema NUNCA SHALL presentar texto ilegible por diseño; ante un color de marca con contraste insuficiente, SHALL advertir al editor pero aun así elegir el texto de mayor contraste disponible.

#### Scenario: Color de marca oscuro
- **WHEN** una organización usa un color de marca oscuro
- **THEN** el texto sobre ese color se presenta en un tono claro que mantiene el contraste legible

#### Scenario: Color de marca claro
- **WHEN** una organización usa un color de marca claro
- **THEN** el texto sobre ese color se presenta en un tono oscuro que mantiene el contraste legible

#### Scenario: Color de marca con contraste insuficiente
- **WHEN** el administrador elige un color que no alcanza el contraste mínimo como color primario
- **THEN** el editor muestra una advertencia sobre el contraste
- **AND** el sistema aplica de todos modos el color de texto de mayor contraste (no queda texto ilegible)

### Requirement: El modo oscuro se preserva con la marca
El sistema SHALL mantener el funcionamiento del modo claro/oscuro con la marca de la organización aplicada. Los colores **semánticos** (éxito, aviso, error) NO SHALL cambiar con la marca.

#### Scenario: Alternar a modo oscuro con marca aplicada
- **WHEN** un usuario con una organización personalizada alterna al modo oscuro
- **THEN** el tema oscuro se presenta con la marca de la organización y contraste legible

#### Scenario: Los estados semánticos no cambian con la marca
- **WHEN** una organización define un color de marca
- **THEN** los colores de éxito, aviso y error se mantienen en sus tonos semánticos (no adoptan el color de marca)

### Requirement: Edición de la marca restringida al administrador
El sistema SHALL permitir consultar la marca de la organización activa a cualquier usuario autenticado, y **actualizarla solo al administrador** de esa organización. La organización sobre la que se opera SHALL ser siempre la del contexto de la sesión, nunca una indicada por el cliente.

#### Scenario: El administrador actualiza la marca
- **WHEN** un usuario con rol `ADMIN` guarda un color de marca, un logo y un favicon válidos
- **THEN** el sistema persiste la marca de su organización
- **AND** la nueva marca queda disponible para aplicarse

#### Scenario: Un no administrador intenta actualizar la marca
- **WHEN** un usuario sin rol `ADMIN` intenta actualizar la marca de su organización
- **THEN** el sistema deniega la operación
- **AND** no modifica la marca

#### Scenario: Valores de entrada inválidos
- **WHEN** el administrador envía un color con formato inválido o una URL malformada
- **THEN** el sistema responde con un error de validación
- **AND** no persiste ningún cambio

#### Scenario: Restaurar un campo al valor por defecto
- **WHEN** el administrador borra el color de marca (lo deja vacío) y guarda
- **THEN** el sistema elimina esa personalización
- **AND** el app shell vuelve a usar el color por defecto del producto para ese campo

### Requirement: La marca solo se aplica tras el login
El sistema SHALL aplicar la marca de la organización únicamente en el app shell (tras autenticarse). Las pantallas de autenticación (login y registro) SHALL conservar la marca del producto, porque antes de autenticar no se conoce la organización.

#### Scenario: Pantallas de auth con marca del producto
- **WHEN** un visitante sin sesión abre las pantallas de login o registro
- **THEN** se presentan con la marca del producto (no la de ninguna organización)

#### Scenario: Reset al cerrar sesión
- **WHEN** un usuario con una organización personalizada cierra sesión
- **THEN** el tema y el favicon vuelven a los del producto

### Requirement: Favicon por organización
El sistema SHALL usar el favicon de la organización activa cuando esté definido, y el del producto en caso contrario.

#### Scenario: Favicon personalizado
- **WHEN** la organización activa tiene un favicon definido y el usuario está autenticado
- **THEN** la pestaña del navegador muestra el favicon de la organización

#### Scenario: Sin favicon definido
- **WHEN** la organización activa no tiene favicon definido
- **THEN** la pestaña del navegador muestra el favicon del producto
