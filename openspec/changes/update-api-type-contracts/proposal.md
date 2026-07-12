## Why

La capacidad `api-type-contracts` existe para que **el contrato OpenAPI sea la fuente única de verdad** entre backend y frontend, de modo que *"el frontend derive sus tipos y su cliente **sin escribirlos a mano**"*. Hoy **incumple ese propósito por cuatro vías**:

1. **El contrato miente sobre los códigos de estado.** Los controllers devuelven el status en runtime (`ResponseEntity`), así que springdoc lo documenta todo como `200`. El registro devuelve **201** y el logout **204**, pero el contrato dice `200`. Un consumidor que confíe en el contrato se equivoca.
2. **El contrato no documenta ni un solo error.** El backend produce errores ricos y uniformes (RFC 9457, con `traceId`), pero **ninguna operación los declara**. Como el contrato no los describe, el cliente generado por orval **no los modela**: el frontend acaba **escribiendo la forma del error a mano** (hoy, literalmente, un `interface ProblemDetailBody { detail?: string }` en `apiError.ts`) — exactamente lo que esta capacidad debía impedir.
3. **El `401` de "no autenticado" NO es RFC 9457.** El entry point de seguridad hace `response.sendError(401)`, que devuelve el cuerpo de error **por defecto del contenedor** (`{timestamp, status, error, path}`), no el `ProblemDetail` del resto de la API. Hay **dos formas de error incompatibles** conviviendo. Documentar ese `401` con el schema común sería **volver a mentir en el contrato**.
4. **Nadie vigila que el contrato refleje el backend.** El job de CI actual (`codegen-drift`) solo verifica **contrato → cliente**. **No verifica backend → contrato** — el propio workflow lo admite en un comentario. Y sin embargo el spec **ya construido** exige *"CI regenera **el contrato** y los tipos y compara con lo versionado"*: **es una promesa incumplida desde el día uno**.

El punto 4 es el que decide si esto sirve de algo: **arreglar el contrato sin arreglar al guardián es tratar el síntoma.** Lo dejaríamos fiel hoy y volvería a mentir con el primer endpoint de `patients`. Por eso este change **cierra también el guardián**, y por eso conviene hacerlo **antes** de `users`, `patients`, `appointments`…

## What Changes

- **Códigos de estado fieles al comportamiento real.** Cada operación declara el status que de verdad devuelve (`201` registro, `204` logout, `200` el resto).
- **Un único formato de error en toda la API.** El `401` de "no autenticado" pasa a emitir **RFC 9457** como el resto (hoy es el único que se sale del formato). Es un cambio pequeño de runtime, pero **necesario**: sin él, el contrato no puede decir la verdad sobre el `401`.
- **Respuestas de error documentadas en el contrato**, con el formato que la API emite:
  - `400` validación · `401` no autenticado · `403` sin permiso · `409` conflicto · `500` inesperado.
- **Dos schemas de error tipados**, en vez de un `object` opaco:
  - el **problema estándar** (RFC 9457: `type`, `title`, `status`, `detail` + las extensiones reales `timestamp` y `traceId`);
  - la **variante de validación**, que añade `errors: [{ field, message }]` para poder pintar el error **junto a cada campo**.
- **Los errores transversales (`401`, `403`, `500`) se aplican de forma central y automática**, no repitiendo anotaciones en cada método: el proyecto aspira a ser un **starter template SaaS** y esto debe escalar a `patients`, `appointments`… **por defecto, no por disciplina**. Los específicos de cada operación (`400`, `409`) se declaran en ella.
- **Guardián real de drift: un job de CI que verifica backend → contrato.** Arranca la app, exporta `/v3/api-docs` y **falla si difiere de `contracts/openapi.json`**. Con esto queda por fin satisfecho el Scenario de detección de drift que la capacidad ya prometía.
- **Contrato y cliente regenerados**, y el frontend **consume los errores desde los tipos generados** (desaparece la forma escrita a mano).

## Capabilities

### New Capabilities

*(ninguna)*

### Modified Capabilities

- `api-type-contracts`: sus requirements hoy solo exigen que el contrato se publique/versione, que **las restricciones de validación de los `*Request`** se reflejen, y que no haya drift (con un Scenario de drift **que no está satisfecho**). Este change añade que el contrato SHALL describir el **status real** de cada operación y SHALL documentar sus **respuestas de error** (con schema tipado y **formato único**), que el frontend SHALL **derivar** también los tipos de error, y **precisa el requirement de drift** para que exija explícitamente las dos direcciones (**backend → contrato** y **contrato → cliente**).

## Impact

- **Backend — `presentation`**: schemas de error nuevos; anotaciones de status y errores en `AuthController` y `BrandingController`; punto central de springdoc para los errores transversales.
- **Backend — `infra/security`**: **`SecurityConfig` cambia el entry point** para emitir RFC 9457 en el `401`. Es el **único cambio de comportamiento en runtime** de este change (ver Non-goals).
- **Backend — `GlobalExceptionHandler`**: sin cambios de comportamiento; ya emite el formato correcto (`docs/backend.md §10`).
- **Contrato**: `contracts/openapi.json` se regenera (crece con las respuestas de error y los schemas).
- **Frontend**: el cliente generado gana los tipos de error; **desaparece `ProblemDetailBody` escrito a mano** en `apiError.ts`; los formularios mapean `errors[]` a errores por campo. Ver `docs/frontend.md` y `docs/coding-style.md §7`.
- **CI**: **job nuevo en el workflow de backend** (export de `/v3/api-docs` + diff contra el contrato versionado). El `codegen-drift` del frontend se mantiene.
- **Tests**: derivados de los Scenarios (status real, errores documentados, formato único de error, herencia de transversales, drift detectado).

## Non-goals

- **No se rediseña el manejo de errores del backend.** `GlobalExceptionHandler` y el formato RFC 9457 se quedan como están. La **única** excepción es el `401` del entry point, que hoy **se sale** de ese formato: se alinea, no se rediseña.
- **No cambia ningún código de estado**: los status que la API devuelve hoy son los que seguirá devolviendo. Lo que cambia es que el contrato los **describe**.
- **No se rediseña la UX de errores del frontend** (toasts, banners, copys). Sí se **añade** el mapeo de `errors[]` a errores por campo — es el consumidor sin el cual el schema `errors[]` sería decorativo —, pero el sistema de notificación existente no se toca.
- **No se añade i18n de mensajes** ni un catálogo de códigos de negocio: se documenta el `type` que ya existe.
- **No se refactorizan los controllers** para dejar de usar `ResponseEntity` (necesario para la cookie del refresh); el status se declara, no se reimplementa.
- **No se documentan los endpoints de `actuator`** ni `/v3/api-docs`.
