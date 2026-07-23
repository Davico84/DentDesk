## Context

`GlobalExceptionHandler` (`presentation/GlobalExceptionHandler.java`) construye el cuerpo de error de **toda** excepción de negocio propia vía la fábrica `ProblemDetails.of(status, errorType, detail)` — `detail` siempre en español, redactado para el usuario (ver `docs/backend.md §10`). Pero extiende `ResponseEntityExceptionHandler`, cuyo padre resuelve **por su cuenta** una lista larga de excepciones de Spring MVC (JSON malformado, 404 sin handler, 405 método no soportado, 415 tipo de contenido, parámetro/cabecera faltante, tipo no convertible, y más) — cada una con su propio `ProblemDetail` y su propio `detail`, generado por Spring en **inglés**, pensado para logs/desarrolladores, no para un usuario final.

El único punto donde el código de este proyecto toca esas respuestas es el override de `handleExceptionInternal` (línea ~105), que hoy solo llama a `ProblemDetails.addExtensions(problem)` (agrega `timestamp`/`traceId`) — **sin tocar `detail`**. Verificado en vivo contra un JSON malformado real:

```json
{"detail":"Failed to read request","instance":"/auth/register","status":400,"title":"Bad Request", ...}
```

El frontend (`getUserFriendlyError`, `docs/frontend.md §2.6`) reenvía ese `detail` sin más a un toast — así que este mensaje en inglés llegaría directo a un usuario hispanohablante.

## Goals / Non-Goals

**Goals:**
- Todo error de la API SHALL tener un `detail` en español, apto para mostrarse directamente al usuario, sin excepción — incluidos los que resuelve el manejador por defecto de Spring **y los que nacen en la capa de Spring Security** (401/403 fuera del controller, ver D4).
- Un solo punto de cambio para los errores de MVC, que cubra automáticamente cualquier excepción nueva que Spring MVC empiece a resolver en el futuro (nuevas versiones, nuevos endpoints), sin tener que acordarse de sobreescribir un método más.

**Non-Goals:**
- No cambia el **status code** de ninguna respuesta (eso ya lo decide correctamente el padre).
- No cambia el **`type`**/`errorType` de las excepciones de negocio ya manejadas explícitamente (`BusinessException`, `AuthenticationException`, etc.) — siguen igual.
- No introduce i18n/multi-idioma: el proyecto es mono-idioma (español), como el resto de mensajes de error existentes.
- No corrige el problema de `instance` (documentación desactualizada, sin impacto en comportamiento) más allá de limpiar los comentarios — no es un cambio de contrato.

## Decisions

### D1 — Mapa status → mensaje en `handleExceptionInternal`, no un override por excepción
`ResponseEntityExceptionHandler` resuelve más de 15 tipos de excepción distintos (JSON malformado, 404, 405, 415, parámetro faltante, cabecera faltante, tipo no convertible, tamaño de archivo excedido, timeout de async...). Sobreescribir cada método individual (`handleHttpMessageNotReadable`, `handleMissingServletRequestParameter`, etc.) sería ~15 métodos casi idénticos y quedaría desactualizado en cuanto Spring MVC introdujera un tipo nuevo.

En vez de eso, `handleExceptionInternal` (el único punto que **ya** intercepta absolutamente todo lo que el padre resuelve, para añadir `timestamp`/`traceId`) gana además un mapa `HttpStatus → detail en español`, y reemplaza el `detail` del `ProblemDetail` antes de devolverlo:

```java
private static final Map<HttpStatus, String> DETAILS_POR_STATUS = Map.of(
    HttpStatus.BAD_REQUEST, "La petición no es válida.",
    HttpStatus.NOT_FOUND, "El recurso solicitado no existe.",
    HttpStatus.METHOD_NOT_ALLOWED, "Operación no permitida para este recurso.",
    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El tipo de contenido enviado no es compatible.",
    HttpStatus.NOT_ACCEPTABLE, "El formato solicitado no es compatible.",
    HttpStatus.PAYLOAD_TOO_LARGE, "El archivo o los datos enviados son demasiado grandes."
    // el resto de statuses que el padre pueda emitir cae en el genérico de abajo
);
private static final String DETAIL_GENERICO = "No se pudo procesar la petición.";
```
- *Por qué mapa por status y no por tipo de excepción:* varios tipos de excepción distintas comparten el mismo status (p. ej. `HttpMessageNotReadableException`, `TypeMismatchException` y `MissingServletRequestParameterException` son todas `400`) — el status es lo que el usuario necesita distinguir, no el nombre interno de la excepción de Spring. Es además **a prueba de futuro**: una excepción nueva que el padre resuelva mañana con un status ya mapeado hereda automáticamente su mensaje correcto, sin tocar código.
- *Consecuencia explícita para QA/mantenimiento:* esto es un **fallback por status, NO una traducción por tipo de excepción**. Dos excepciones distintas con el mismo status **comparten** mensaje a propósito. Si algún día un caso concreto merece un mensaje propio distinto del de su status, **no** se cambia el mapa: se añade un `@ExceptionHandler` puntual para ese tipo (que ya no pasa por `handleExceptionInternal`, ver D2). El mapa es la base, no la única vía.
- *Fallback genérico:* si el status no está en el mapa (caso no anticipado), un mensaje neutro en español — nunca el texto crudo de Spring.
- *Fragilidad técnica a cuidar — `HttpStatusCode` ≠ `HttpStatus`:* `handleExceptionInternal` recibe un `HttpStatusCode`, no el enum `HttpStatus`. La búsqueda en el mapa SHALL resolver primero con `HttpStatus.resolve(statusCode.value())`, que devuelve `null` para statuses no estándar — en cuyo caso se usa el `DETAIL_GENERICO`. No asumir que el status siempre es un `HttpStatus` del enum; nunca dejar que un `null` reviente el handler.
- *Alternativa descartada — override por excepción:* más preciso por caso (podría decir exactamente qué parámetro faltó), pero es ~15 métodos que mantener y que se desactualizan solos. Se puede añadir un override puntual **más adelante** para un caso específico que de verdad lo necesite (p. ej. si un mensaje por parámetro faltante mejora mucho la UX de un formulario concreto), sin descartar el mapa por status como base.

### D2 — No tocar los mensajes ya curados de `GlobalExceptionHandler`
El mapa de D1 solo aplica dentro de `handleExceptionInternal`, que **únicamente** procesa lo que el padre (`ResponseEntityExceptionHandler`) resuelve. Los `@ExceptionHandler` propios (`handleBusiness`, `handleAuthentication`, `handleAccessDenied`, `handleDataIntegrity`, `handleUncaught`) ya construyen su `detail` a mano vía `ProblemDetails.of()` y no pasan por `handleExceptionInternal` — sin cambios.

### D4 — El alcance incluye los errores de Spring Security (no solo los de MVC)
"Toda la API" abarca dos caminos distintos de generación de errores, y hay que cubrir **ambos**:

1. **MVC** (`ResponseEntityExceptionHandler` → `handleExceptionInternal`): lo que arregla D1.
2. **Spring Security** (antes/alrededor del controller): entra por sus propios puntos de extensión, **no** por `handleExceptionInternal`.

Estado verificado hoy (en código, no supuesto):

| Error de seguridad | Camino actual | Cumple hoy |
|---|---|---|
| `401` sin sesión / token inválido | `authenticationEntryPoint` **delega** en el `handlerExceptionResolver` → `@ExceptionHandler(AuthenticationException.class)` con `detail` en español | ✅ (tests en `AuthControllerIT`/`ErrorContractIT`) |
| `403` de method-security (`@PreAuthorize`) | lanza `AccessDeniedException` **dentro** del controller → `@ExceptionHandler(AccessDeniedException.class)` en español | ✅ |
| `403` a nivel **URL** (regla de rol en `authorizeHttpRequests`) | **no hay `accessDeniedHandler` configurado** → usa el cuerpo por defecto del contenedor | ⚠️ **hueco latente** |

- *Decisión:* cerrar el hueco **simétricamente**, configurando el `accessDeniedHandler` para que **delegue en el `handlerExceptionResolver`** igual que ya hace el `authenticationEntryPoint`. Es el mismo patrón de una línea, y hace que un `403` por URL salga como el resto (RFC 9457 + `detail` en español) el día que se añada la primera regla de rol por URL.
- *Por qué ahora y no después:* hoy no se dispara (solo `permitAll` + `authenticated`), pero la capacidad **`staff`** traerá reglas de rol; dejar el hueco abierto significa que el primer endpoint restringido filtraría el cuerpo por defecto sin que nadie lo note. Cerrarlo aquí, mientras tocamos justo el manejo de errores, es barato y evita una fuga futura.
- *Alternativa descartada — dejarlo fuera de alcance hasta que exista una regla por URL:* el change se llama "toda la API falla con el mismo formato/contenido"; dejar un camino conocido sin cubrir contradice su propio requirement.

### D3 — Limpieza de comentarios sobre `instance` (sin cambio de comportamiento)
`ProblemDetails.java:22` y el comentario de clase en `ErrorContractIT.java:35` afirman *"`instance` NO se emite"*; un test más nuevo en el mismo `ErrorContractIT.java` (líneas ~61-64) ya lo corrigió puntualmente ("SÍ se emite, Spring lo rellena solo"). Se actualizan las dos afirmaciones viejas para que no se contradigan con el test que las corrige. Puro comentario — no altera el schema del contrato (`instance` ya figura documentado, la generación del contrato no cambia).

## Risks / Trade-offs

- **Mensaje genérico pierde precisión** frente al de Spring (que a veces nombra el campo/parámetro exacto) → aceptado: la audiencia es un usuario final, no un desarrollador; el `traceId` ya existe para diagnóstico interno vía logs.
- **Nuevo status no anticipado** cae en el fallback genérico, no en un mensaje específico → aceptado; se amplía el mapa cuando aparezca un caso real, no por anticipación especulativa.
- **Status no estándar en `handleExceptionInternal`** (`HttpStatus.resolve(...)` devuelve `null`) → mitigado por el `DETAIL_GENERICO`; el handler nunca debe romperse por un `null` (ver D1).
- **La capa de seguridad es una vía distinta de la de MVC** (D4) → mitigado configurando el `accessDeniedHandler` para delegar en el resolver, igual que el entry point; queda cubierta por un test de seguridad (401 y 403 por seguridad), no solo los de MVC.
