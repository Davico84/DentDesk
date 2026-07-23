## Why

Investigando cómo el frontend debía mostrar el `409` de documento duplicado (`patients`), se verificó en vivo (no solo leyendo código) que un `400` de JSON malformado responde:

```json
{"detail":"Failed to read request", "instance":"/auth/register", "status":400, ...}
```

`"Failed to read request"` es el mensaje interno de Spring, en inglés, sin pasar por la fábrica `ProblemDetails.of()` del backend. La capacidad `api-type-contracts` ya exige que **toda la API falle con el mismo formato** (RFC 9457), pero no exige nada sobre que el **contenido** de `detail` sea apto para mostrarse al usuario final. El frontend (`getUserFriendlyError`, `docs/frontend.md §2.6`) asume que `detail` **siempre** viene "redactado para el usuario" — cierto para las excepciones que `GlobalExceptionHandler` maneja explícitamente, falso para cualquiera que resuelva el manejador **por defecto** de Spring MVC (JSON malformado, 405, 415, parámetro faltante, y el resto de la lista larga de `ResponseEntityExceptionHandler`).

De paso, se encontró una inexactitud de documentación sin relación de comportamiento: el Javadoc de `ProblemDetails.java` y un comentario de clase en `ErrorContractIT.java` afirman que el campo `instance` "nunca se emite" — falso, Spring lo rellena solo con la URI al renderizar el `ProblemDetail`; un test más nuevo en el mismo archivo ya lo corrigió puntualmente, pero las dos afirmaciones viejas quedaron sin limpiar.

## What Changes

- El backend SHALL responder un `detail` apto para el usuario final en **todos** los errores, incluidos los que resuelve el manejador por defecto de Spring MVC — no solo los que `GlobalExceptionHandler` declara explícitamente.
- Centralizado en el único punto que ya existe para esto (`handleExceptionInternal`, el override que añade `timestamp`/`traceId` a los errores del padre): un mapa **status → mensaje en español**, en vez de sobreescribir cada tipo de excepción del padre uno por uno (~15+ tipos en `ResponseEntityExceptionHandler`).
- Limpieza de las dos afirmaciones desactualizadas sobre `instance` (no cambia comportamiento, solo corrige documentación que se contradice con un test ya corregido en el mismo archivo).

## Capabilities

### New Capabilities
<!-- ninguna -->

### Modified Capabilities
- `api-type-contracts`: se modifica el requirement **Toda la API falla con un único formato de error** para exigir, además de la forma (RFC 9457), que el **contenido** de `detail` sea apto para el usuario final en cualquier excepción, incluidas las que resuelve el manejador por defecto de Spring.

## Impact

- **Backend**: `GlobalExceptionHandler.handleExceptionInternal` — mapa status→mensaje aplicado antes de renderizar. `ProblemDetails.java` y `ErrorContractIT.java` — corrección de comentarios sobre `instance`.
- **Frontend**: sin cambios. `getUserFriendlyError` ya está correctamente implementado (`docs/frontend.md §2.6`); el problema era exclusivamente la calidad del `detail` que el backend le entrega.
- **Tests**: nuevo Scenario + test para el path del manejador por defecto (JSON malformado ya cubierto por `ErrorContractIT`, se le suma la aserción de contenido).
