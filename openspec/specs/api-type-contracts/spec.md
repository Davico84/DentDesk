# api-type-contracts Specification

## Purpose

Establece el contrato de API como fuente única de verdad entre backend y frontend: el backend publica y versiona su especificación OpenAPI **describiendo fielmente lo que hace** (el status real de cada operación y las respuestas de error que produce), el frontend deriva de ella sus tipos y su cliente —incluidos los **tipos de error**— sin escribirlos a mano, y CI detecta el drift **en las dos direcciones** de la cadena (backend → contrato → cliente). Para que el contrato pueda describir los errores con un único schema, **toda la API falla con el mismo formato** (RFC 9457).

## Requirements

### Requirement: Especificación OpenAPI publicada y versionada
El backend SHALL exponer su especificación OpenAPI en runtime y exportarla a un snapshot **versionado en Git**, que sirve como contrato fuente único para el frontend.

#### Scenario: Exposición en runtime
- **WHEN** se realiza una petición GET a `/v3/api-docs` con la aplicación en ejecución
- **THEN** el backend responde `200 OK` con un documento OpenAPI válido
- **AND** el documento incluye las operaciones de autenticación (registro, login, refresco)

#### Scenario: Snapshot del contrato versionado
- **WHEN** se ejecuta la tarea de export del contrato del backend
- **THEN** se genera/actualiza `contracts/openapi.json` (documento OpenAPI válido)
- **AND** ese archivo está versionado en Git (no bajo `target/`), como fuente para el codegen del frontend

#### Scenario: Restricciones de validación reflejadas en el contrato
- **WHEN** se inspecciona el schema de un DTO `*Request` en `contracts/openapi.json` (p. ej. el de registro)
- **THEN** las restricciones de formato del DTO (longitud, formato de correo, campos requeridos) aparecen reflejadas en el schema (paridad con `docs/coding-style.md §7`)

### Requirement: El contrato declara el código de estado real de cada operación
El contrato SHALL documentar, para cada operación, el **código de estado HTTP que la API devuelve realmente** en el camino feliz. El contrato NO SHALL documentar un código de éxito que la API no emite. Un consumidor que confíe en el contrato no debe equivocarse sobre el status.

#### Scenario: El registro se documenta como 201
- **WHEN** se inspecciona la operación de registro en `contracts/openapi.json`
- **THEN** su respuesta de éxito es **`201`**
- **AND** no aparece una respuesta `200` (el `200` por defecto de la herramienta no debe sobrevivir)

#### Scenario: El logout se documenta como 204
- **WHEN** se inspecciona la operación de logout en `contracts/openapi.json`
- **THEN** su respuesta de éxito es **`204`** y sin cuerpo
- **AND** no aparece una respuesta `200`

#### Scenario: Las operaciones que devuelven 200 lo siguen documentando
- **WHEN** se inspeccionan las operaciones que en runtime devuelven `200` (login, refresco, consulta y actualización de marca)
- **THEN** su respuesta de éxito documentada es `200`

#### Scenario: El status documentado coincide con el real
- **WHEN** se ejecuta cada operación contra la API y se compara el status obtenido con el documentado en el contrato
- **THEN** coinciden para todas las operaciones

### Requirement: Toda la API falla con un único formato de error
El sistema SHALL responder **todos** sus errores con el mismo formato (RFC 9457), sin excepciones. Ninguna respuesta de error SHALL usar el formato por defecto del contenedor. Sin esta uniformidad, el contrato no puede describir los errores con un único schema y el cliente no puede tipar el manejo de errores.

#### Scenario: El error de sesión ausente usa el formato común
- **WHEN** se solicita un recurso protegido **sin autenticarse**
- **THEN** la respuesta es `401`
- **AND** su cuerpo tiene el **mismo formato** que el resto de errores de la API (RFC 9457), no el cuerpo por defecto del contenedor

#### Scenario: Un error de negocio usa el formato común
- **WHEN** se intenta iniciar sesión con credenciales inválidas
- **THEN** la respuesta es `401` con el formato común (RFC 9457)

#### Scenario: Un error de permisos usa el formato común
- **WHEN** un usuario autenticado sin el rol requerido invoca una operación restringida
- **THEN** la respuesta es `403` con el formato común (RFC 9457)

### Requirement: El contrato documenta las respuestas de error de cada operación
El contrato SHALL documentar las **respuestas de error** que cada operación puede producir, con un **schema tipado** (nunca un objeto opaco) y **fiel al cuerpo real**. Los errores **transversales** (`401` sin sesión, `403` sin permiso, `500` inesperado) SHALL aplicarse **automáticamente** a las operaciones que correspondan, sin exigir que el autor de un endpoint nuevo los declare a mano.

#### Scenario: Error de validación con detalle por campo
- **WHEN** se inspecciona una operación que valida su cuerpo (p. ej. el registro) en `contracts/openapi.json`
- **THEN** documenta una respuesta **`400`**
- **AND** su schema permite una lista `errors` de elementos con `field` y `message`, para mostrar el error junto a cada campo

#### Scenario: Conflicto en el registro
- **WHEN** se inspecciona la operación de registro
- **THEN** documenta una respuesta **`409`**
- **AND** su `type` permite distinguir el conflicto de correo del de identificador de organización

#### Scenario: Operación protegida sin autenticar
- **WHEN** se inspecciona una operación que requiere sesión (p. ej. la consulta de marca)
- **THEN** documenta una respuesta **`401`**

#### Scenario: Operación restringida por rol
- **WHEN** se inspecciona una operación restringida a un rol (p. ej. la actualización de marca, solo administrador)
- **THEN** documenta una respuesta **`403`**

#### Scenario: Los errores transversales se heredan sin declararlos
- **WHEN** una operación que requiere autenticación **no declara** en su código ninguna respuesta de error
- **THEN** el contrato la documenta igualmente con `401` y `500`
- **AND** si además restringe por rol, también con `403`

#### Scenario: El schema de error describe el cuerpo real
- **WHEN** se provoca un error en la API (uno de validación y uno estándar) y se comparan los campos del cuerpo devuelto con el schema documentado
- **THEN** el cuerpo real solo contiene campos descritos por el schema
- **AND** el contrato **no** documenta ningún campo que la API nunca emita
- **AND** los campos que la API emite solo a veces (como el identificador de traza) figuran como **opcionales**, no como obligatorios

### Requirement: Tipos y cliente del frontend generados desde el contrato versionado
El frontend SHALL derivar sus tipos y sus funciones de acceso a la API a partir del contrato versionado (`contracts/openapi.json`), sin escribirlos a mano, manteniéndose desacoplado del build del backend. Los hooks de TanStack Query se componen a mano sobre esas funciones.

#### Scenario: Generación desde el contrato versionado
- **WHEN** se ejecuta el script `generate:api` del frontend contra `contracts/openapi.json`
- **THEN** se generan los tipos y las funciones fetch tipadas en `src/modules/core/services/generated`
- **AND** el proyecto pasa `pnpm typecheck` usando esos artefactos generados

#### Scenario: El consumo de la API usa los artefactos generados
- **WHEN** una pantalla o módulo del frontend invoca un endpoint de autenticación
- **THEN** lo hace a través de las **funciones y tipos generados** (los hooks de TanStack Query se componen a mano sobre esas funciones), no mediante llamadas `axios` escritas a mano con tipos manuales

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

### Requirement: Contrato y artefactos generados sincronizados (sin drift)
El repositorio SHALL versionar los archivos TypeScript generados y SHALL detectar en CI cuando el contrato o los artefactos generados quedan desactualizados. La detección SHALL cubrir **las dos direcciones** de la cadena: que el **contrato versionado refleje el backend real**, y que el **cliente generado refleje el contrato versionado**. Que solo se vigile una dirección deja al contrato libre de mentir sobre el backend.

#### Scenario: Artefactos generados versionados
- **WHEN** se inspecciona `src/modules/core/services`
- **THEN** los archivos generados por el codegen están versionados en Git
- **AND** el pipeline del frontend (typecheck/lint/test) se ejecuta sin necesidad del build del backend

#### Scenario: Detección de drift entre el backend y el contrato
- **WHEN** CI obtiene la especificación OpenAPI del backend actual y la compara con `contracts/openapi.json`
- **THEN** si hay diferencias, el pipeline **falla**, indicando que el contrato versionado no refleja el backend

#### Scenario: Detección de drift entre el contrato y el cliente generado
- **WHEN** CI regenera el cliente del frontend desde `contracts/openapi.json` y lo compara con lo versionado
- **THEN** si hay diferencias, el pipeline **falla**, indicando que el cliente generado está desactualizado

#### Scenario: Un endpoint nuevo sin regenerar el contrato rompe CI
- **WHEN** se añade o modifica una operación en el backend y **no** se regenera `contracts/openapi.json`
- **THEN** CI falla, impidiendo que el contrato quede desincronizado del backend
