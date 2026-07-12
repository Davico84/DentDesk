## Why

La capacidad `api-type-contracts` existe para que **el contrato OpenAPI sea la fuente única de verdad** entre backend y frontend, de modo que *"el frontend derive sus tipos y su cliente **sin escribirlos a mano**"*. Hoy el contrato **incumple ese propósito por dos vías**:

1. **Miente sobre los códigos de estado.** Los controllers devuelven el status en runtime (`ResponseEntity`), así que springdoc lo documenta todo como `200`. El registro devuelve **201** y el logout **204**, pero el contrato dice `200` en ambos. Un consumidor que confíe en el contrato se equivoca.
2. **No documenta ni un solo error.** El backend produce errores ricos y uniformes (RFC 9457 `ProblemDetail`, con `traceId`), pero **ninguna operación los declara**. Como el contrato no los describe, el cliente generado por orval **no los modela**: el frontend acaba tipando errores a mano o tratándolos a ciegas — exactamente lo que esta capacidad debía impedir.

El coste crece con cada capacidad nueva. Antes de construir `users`, `patients`, `appointments`… conviene fijar el contrato de errores **una vez** y que las capacidades futuras lo hereden.

## What Changes

- **Códigos de estado fieles al comportamiento real.** Cada operación declara el status que de verdad devuelve (`201` en registro, `204` en logout, `200` en el resto).
- **Respuestas de error documentadas en el contrato.** Cada operación declara los errores que puede producir, con el formato RFC 9457 que ya emite `GlobalExceptionHandler`:
  - `400` validación · `401` no autenticado · `403` sin permiso · `409` conflicto (correo o slug duplicado) · `500` error inesperado.
- **Dos schemas de error tipados**, en vez de un `object` opaco:
  - el **problema estándar** (RFC 9457: `type`, `title`, `status`, `detail`, `instance` + las extensiones `timestamp` y `traceId`);
  - la **variante de validación**, que añade `errors: [{ field, message }]` para que el frontend pueda pintar el error **junto a cada campo**.
- **Mecanismo DRY para los errores transversales.** Los errores comunes (`401`, `403`, `500`) se aplican de forma central, no repitiendo anotaciones en cada método: el proyecto aspira a ser un **starter template SaaS** y esto debe escalar sin fricción a `patients`, `appointments`, etc. Los errores específicos de una operación (`400`, `409`) se declaran en ella.
- **Contrato y cliente regenerados**: `contracts/openapi.json` y el cliente del frontend (`generate:api`).
- **El frontend consume los errores desde los tipos generados**, no escritos a mano (cierra el círculo del propósito de la capacidad).

No hay cambios en el comportamiento del backend en runtime: **los status y los cuerpos de error que ya se devuelven no cambian**. Lo que cambia es que el contrato por fin los **describe**. No hay BREAKING changes para el backend; sí un cambio en los tipos generados que consume el frontend (ver Impact).

## Capabilities

### New Capabilities

*(ninguna)*

### Modified Capabilities

- `api-type-contracts`: sus requirements hoy solo exigen que el contrato se publique/versione, que **las restricciones de validación de los `*Request`** se reflejen en los schemas, y que no haya drift. **No dicen nada de códigos de estado ni de respuestas de error.** Este change añade que el contrato SHALL describir fielmente el status real de cada operación y SHALL documentar sus respuestas de error (con schema tipado), de modo que el frontend **derive** también los tipos de error en vez de escribirlos a mano.

## Impact

- **Backend — capa `presentation`**: anotaciones de documentación en los controllers (`AuthController`, `BrandingController`) y un punto central de configuración de springdoc para los errores transversales. Nuevos DTO/schema de error. **Sin cambios en `service`, `persistence` ni `infra`**; `GlobalExceptionHandler` ya produce el formato correcto (ver `docs/backend.md §10`).
- **Contrato**: `contracts/openapi.json` se regenera (crece con las respuestas de error y los schemas nuevos).
- **Frontend**: el cliente generado (`src/modules/core/services/generated`) gana los tipos de error; el manejo de errores pasa a apoyarse en ellos. Ver `docs/frontend.md` (capa API generada) y `docs/coding-style.md §7` (paridad de validación).
- **CI**: el job de detección de drift del contrato debe seguir en verde tras regenerar (`docs/tooling-setup.md`).
- **Tests**: derivados de los Scenarios del spec (contrato expone el status real; contrato declara los errores; el 400 de validación expone `errors[]`).

## Non-goals

- **No se cambia el comportamiento de la API**: ni un status ni un cuerpo de error distinto de los actuales. Esto es *documentar la verdad*, no redefinirla.
- **No se rediseña el manejo de errores del backend.** `GlobalExceptionHandler` y el formato RFC 9457 se quedan como están (`docs/backend.md §10`).
- **No se añade i18n de mensajes de error** ni un catálogo de códigos de error de negocio: se documenta el `type`/`errorType` que ya existe.
- **No se rediseña la UX de errores del frontend** (toasts, banners…). Solo se pasa a **tipar** lo que ya se maneja; el rediseño visual queda fuera.
- **No se tocan los endpoints de `actuator`** ni el propio `/v3/api-docs`.
