## Context

`GlobalExceptionHandler` (`presentation/GlobalExceptionHandler.java`) construye el cuerpo de error de **toda** excepción de negocio propia vía la fábrica `ProblemDetails.of(status, errorType, detail)` — `detail` siempre en español, redactado para el usuario (ver `docs/backend.md §10`). Pero extiende `ResponseEntityExceptionHandler`, cuyo padre resuelve **por su cuenta** una lista larga de excepciones de Spring MVC (JSON malformado, 404 sin handler, 405 método no soportado, 415 tipo de contenido, parámetro/cabecera faltante, tipo no convertible, y más) — cada una con su propio `ProblemDetail` y su propio `detail`, generado por Spring en **inglés**, pensado para logs/desarrolladores, no para un usuario final.

El único punto donde el código de este proyecto toca esas respuestas es el override de `handleExceptionInternal` (línea ~105), que hoy solo llama a `ProblemDetails.addExtensions(problem)` (agrega `timestamp`/`traceId`) — **sin tocar `detail`**. Verificado en vivo contra un JSON malformado real:

```json
{"detail":"Failed to read request","instance":"/auth/register","status":400,"title":"Bad Request", ...}
```

El frontend (`getUserFriendlyError`, `docs/frontend.md §2.6`) reenvía ese `detail` sin más a un toast — así que este mensaje en inglés llegaría directo a un usuario hispanohablante.

## Goals / Non-Goals

**Goals:**
- Todo error de la API SHALL tener un `detail` en español, apto para mostrarse directamente al usuario, sin excepción — incluidos los que resuelve el manejador por defecto de Spring.
- Un solo punto de cambio, que cubra automáticamente cualquier excepción nueva que Spring MVC empiece a resolver en el futuro (nuevas versiones, nuevos endpoints), sin tener que acordarse de sobreescribir un método más.

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
- *Fallback genérico:* si el status no está en el mapa (caso no anticipado), un mensaje neutro en español — nunca el texto crudo de Spring.
- *Alternativa descartada — override por excepción:* más preciso por caso (podría decir exactamente qué parámetro faltó), pero es ~15 métodos que mantener y que se desactualizan solos. Se puede añadir un override puntual **más adelante** para un caso específico que de verdad lo necesite (p. ej. si un mensaje por parámetro faltante mejora mucho la UX de un formulario concreto), sin descartar el mapa por status como base.

### D2 — No tocar los mensajes ya curados de `GlobalExceptionHandler`
El mapa de D1 solo aplica dentro de `handleExceptionInternal`, que **únicamente** procesa lo que el padre (`ResponseEntityExceptionHandler`) resuelve. Los `@ExceptionHandler` propios (`handleBusiness`, `handleAuthentication`, `handleAccessDenied`, `handleDataIntegrity`, `handleUncaught`) ya construyen su `detail` a mano vía `ProblemDetails.of()` y no pasan por `handleExceptionInternal` — sin cambios.

### D3 — Limpieza de comentarios sobre `instance` (sin cambio de comportamiento)
`ProblemDetails.java:22` y el comentario de clase en `ErrorContractIT.java:35` afirman *"`instance` NO se emite"*; un test más nuevo en el mismo `ErrorContractIT.java` (líneas ~61-64) ya lo corrigió puntualmente ("SÍ se emite, Spring lo rellena solo"). Se actualizan las dos afirmaciones viejas para que no se contradigan con el test que las corrige. Puro comentario — no altera el schema del contrato (`instance` ya figura documentado, la generación del contrato no cambia).

## Risks / Trade-offs

- **Mensaje genérico pierde precisión** frente al de Spring (que a veces nombra el campo/parámetro exacto) → aceptado: la audiencia es un usuario final, no un desarrollador; el `traceId` ya existe para diagnóstico interno vía logs.
- **Nuevo status no anticipado** cae en el fallback genérico, no en un mensaje específico → aceptado; se amplía el mapa cuando aparezca un caso real, no por anticipación especulativa.
