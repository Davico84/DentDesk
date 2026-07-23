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
- **Cubre también los errores de Spring Security** (401/403 que nacen fuera del controller): se verifica que el `401` y el `403` de method-security ya salen con `detail` en español (delegan en el resolver de MVC), y se **cierra el hueco latente del `403` a nivel URL** configurando el `accessDeniedHandler` para que delegue igual que ya hace el `authenticationEntryPoint`. Ver design D4.
- Limpieza de las dos afirmaciones desactualizadas sobre `instance` (no cambia comportamiento, solo corrige documentación que se contradice con un test ya corregido en el mismo archivo).

## Capabilities

### New Capabilities
<!-- ninguna -->

### Modified Capabilities
- `api-type-contracts`: se modifica el requirement **Toda la API falla con un único formato de error** para exigir, además de la forma (RFC 9457), que el **contenido** de `detail` sea apto para el usuario final en cualquier excepción, incluidas las que resuelve el manejador por defecto de Spring.

## Impact

- **Backend**: `GlobalExceptionHandler.handleExceptionInternal` — mapa status→mensaje aplicado antes de renderizar (resolviendo `HttpStatusCode`→`HttpStatus` con fallback). `SecurityConfig` — configurar el `accessDeniedHandler` para delegar en el `handlerExceptionResolver` (D4). `ProblemDetails.java` y `ErrorContractIT.java` — corrección de comentarios sobre `instance`.
- **Frontend**: sin cambios. `getUserFriendlyError` ya está correctamente implementado (`docs/frontend.md §2.6`); el problema era exclusivamente la calidad del `detail` que el backend le entrega.
- **Tests**: nuevo Scenario + test para el path del manejador por defecto (JSON malformado ya cubierto por `ErrorContractIT`, se le suma la aserción de contenido) **y para el camino de seguridad** (401/403 salen en español, incluido el 403 por URL una vez configurado el handler).
