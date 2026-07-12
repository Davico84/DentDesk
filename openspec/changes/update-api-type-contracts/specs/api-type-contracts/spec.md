## ADDED Requirements

### Requirement: El contrato declara el código de estado real de cada operación
El contrato SHALL documentar, para cada operación, el **código de estado HTTP que la API devuelve realmente** en el camino feliz. El contrato NO SHALL documentar un código de éxito que la API no emite. Un consumidor que confíe en el contrato no debe equivocarse sobre el status.

#### Scenario: El registro se documenta como 201
- **WHEN** se inspecciona la operación `POST /auth/register` en `contracts/openapi.json`
- **THEN** su respuesta de éxito es **`201`**
- **AND** no aparece una respuesta `200` (el `200` por defecto de la herramienta no debe sobrevivir)

#### Scenario: El logout se documenta como 204
- **WHEN** se inspecciona la operación `POST /auth/logout` en `contracts/openapi.json`
- **THEN** su respuesta de éxito es **`204`** y sin cuerpo
- **AND** no aparece una respuesta `200`

#### Scenario: Las operaciones que devuelven 200 lo siguen documentando
- **WHEN** se inspeccionan las operaciones que en runtime devuelven `200` (login, refresco, consulta y actualización de marca)
- **THEN** su respuesta de éxito documentada es `200`

#### Scenario: El status documentado coincide con el real
- **WHEN** se ejecuta cada operación contra la API y se compara el status obtenido con el documentado en el contrato
- **THEN** coinciden para todas las operaciones

### Requirement: El contrato documenta las respuestas de error de cada operación
El contrato SHALL documentar las **respuestas de error** que cada operación puede producir, con un **schema tipado** (nunca un objeto opaco), en el formato RFC 9457 que la API ya emite. Los errores **transversales** (`401` no autenticado, `403` sin permiso, `500` inesperado) SHALL aplicarse **de forma automática** a las operaciones que correspondan, sin exigir que el autor de un endpoint nuevo los declare a mano.

#### Scenario: Error de validación con detalle por campo
- **WHEN** se inspecciona una operación que valida su cuerpo (p. ej. el registro) en `contracts/openapi.json`
- **THEN** documenta una respuesta **`400`**
- **AND** su schema incluye una lista `errors` de elementos con `field` y `message`, para poder mostrar el error junto a cada campo

#### Scenario: Conflicto en el registro
- **WHEN** se inspecciona la operación `POST /auth/register`
- **THEN** documenta una respuesta **`409`** (correo o identificador de organización ya existentes)

#### Scenario: Operación protegida sin autenticar
- **WHEN** se inspecciona una operación que requiere sesión (p. ej. la consulta de marca)
- **THEN** documenta una respuesta **`401`**

#### Scenario: Operación restringida por rol
- **WHEN** se inspecciona una operación restringida a un rol (p. ej. la actualización de marca, solo administrador)
- **THEN** documenta una respuesta **`403`**

#### Scenario: Los errores transversales se heredan sin declararlos
- **WHEN** se añade al sistema una operación nueva que requiere autenticación, **sin** declarar en ella ninguna respuesta de error
- **THEN** el contrato la documenta igualmente con `401` y `500`
- **AND** si además restringe por rol, también con `403`

#### Scenario: El schema de error describe el cuerpo real
- **WHEN** se provoca un error en la API (uno de validación y uno estándar) y se comparan los campos del cuerpo devuelto con el schema de error documentado
- **THEN** el cuerpo real solo contiene campos descritos por el schema (incluidos `traceId` y la marca de tiempo)
- **AND** no hay campos documentados que la API nunca emita

### Requirement: El frontend deriva del contrato también los tipos de error
El frontend SHALL derivar del contrato versionado los **tipos de las respuestas de error**, sin escribirlos a mano, y SHALL apoyarse en ellos para manejar los errores de la API. Los errores de validación por campo SHALL poder mapearse al campo correspondiente del formulario.

#### Scenario: Los tipos de error salen del codegen
- **WHEN** se ejecuta el script de generación del cliente contra `contracts/openapi.json`
- **THEN** se generan los tipos de las respuestas de error junto a los de las peticiones y respuestas
- **AND** el proyecto pasa el typecheck usándolos

#### Scenario: El manejo de errores usa los tipos generados
- **WHEN** se inspecciona cómo el frontend maneja un error de la API
- **THEN** lo hace a través de los **tipos generados** desde el contrato
- **AND** no existe ninguna declaración de la forma del error escrita a mano

#### Scenario: Un error de validación del servidor se muestra junto al campo
- **WHEN** el servidor rechaza un formulario con un error de validación sobre un campo concreto
- **THEN** el formulario presenta ese mensaje **junto al campo** al que corresponde
