## Context

`api-type-contracts` ya está construida: el backend publica `/v3/api-docs`, se versiona en `contracts/openapi.json`, y el frontend genera tipos + funciones con orval (`docs/frontend.md`). `GlobalExceptionHandler` emite errores en **RFC 9457** con las extensiones `timestamp` y `traceId` (`docs/backend.md §10`).

Al auditar el estado real antes de implementar, aparecieron **dos huecos que el diseño inicial daba por cerrados** y que cambian el alcance:

1. **El `401` de "no autenticado" no es RFC 9457.** `SecurityConfig` hace `response.sendError(401)`, que delega en el despacho de error del contenedor y devuelve `{timestamp, status, error, path}` — **no** un `ProblemDetail`. Conviven **dos formas de error incompatibles**: la del `401` de sesión y la de todo lo demás (incluido el `401` *semántico* de `login`/`refresh`, que sí es RFC 9457 porque nace de una excepción de negocio).
2. **Nadie verifica que el contrato refleje el backend.** El job `codegen-drift` solo comprueba **contrato → cliente**; el propio workflow lo admite: *"La sincronización contrato ↔ backend real se verificará en un job del backend cuando se automatice el export de /v3/api-docs"*. Pero el **spec ya construido** exige *"CI regenera **el contrato** y los tipos y compara con lo versionado"* → **Scenario incumplido desde el día uno**.

El hueco 2 es el que decide si este change sirve de algo: **sin guardián, el contrato vuelve a mentir con el primer endpoint de `patients`.**

Restricción de escala: el proyecto aspira a ser **starter template SaaS**. Si documentar errores exige que cada dev recuerde repetir anotaciones, el contrato se desincronizará. **La solución tiene que ser por defecto, no por disciplina.**

## Goals / Non-Goals

**Goals:**

- El contrato declara el **status real** de cada operación (`201` registro, `204` logout, `200` resto).
- **Un único formato de error en toda la API** (RFC 9457), incluido el `401` de sesión.
- El contrato declara las **respuestas de error** con **schema tipado** (no un `object` opaco) y **fiel al cuerpo real**.
- Los transversales (`401`, `403`, `500`) se aplican **central y automáticamente**: una capacidad futura los hereda sin escribir nada.
- El error de validación expone `errors: [{ field, message }]` y el frontend lo pinta **junto a cada campo**.
- **CI detecta el drift backend → contrato** (el guardián que faltaba).

**Non-Goals:**

- Rediseñar `GlobalExceptionHandler` ni el formato RFC 9457. La **única** excepción es alinear el `401` del entry point, que hoy **se sale** del formato.
- Cambiar cualquier **código de estado** que la API ya devuelve.
- Rediseñar la UX de errores (toasts, banners, copys). Sí se **añade** el mapeo `errors[]` → campo, sin el cual el schema sería decorativo.
- Refactorizar los controllers para dejar de usar `ResponseEntity` (lo necesitan para la cookie del refresh).

## Decisions

### D1 — El status real se declara con `@ApiResponse`, no con `@ResponseStatus` ni refactorizando los controllers

Los controllers necesitan `ResponseEntity` porque **añaden la cookie `Set-Cookie`** del refresh (`RefreshCookieFactory`).

**Decisión:** declarar el status con la anotación de documentación `@ApiResponse(responseCode = "201", ...)`.

- *Alternativa A — `@ResponseStatus(HttpStatus.CREATED)`:* springdoc **sí** la lee, pero en runtime Spring la **ignora** cuando el método devuelve `ResponseEntity`. Sería **otra anotación que miente**: justo la trampa que estamos eliminando. **Rechazada.**
- *Alternativa B — refactorizar los controllers* (status vía `@ResponseStatus`, cookie vía `HttpServletResponse`): más idiomático, pero cambia runtime sin necesidad. **Rechazada** (podría ser un change propio).

**Verificación obligatoria:** springdoc añade una respuesta `200` **por defecto**. Al declarar `201`/`204` hay que comprobar que **el `200` fantasma desaparece** y no conviven ambas. Es una tarea, no un supuesto.

### D2 — Schemas de error propios y **fieles al cuerpo real**

El `ProblemDetail` de Spring guarda las extensiones (`timestamp`, `traceId`) en un mapa dinámico (`setProperty`), que **springdoc no ve**: su schema autogenerado saldría **incompleto**.

**Decisión:** definir dos schemas de documentación en `presentation`:

- **`ApiProblem`** — `type`, `title`, `status`, `detail` (RFC 9457) **+ `timestamp` y `traceId`**.
- **`ValidationProblem`** — lo anterior **+ `errors: [{ field, message }]`**.

**Precisiones de fidelidad** (salieron de auditar `GlobalExceptionHandler`; sin ellas el schema mentiría, violando nuestro propio Scenario *"no hay campos documentados que la API nunca emita"*):

- **`instance` NO se documenta**: el handler **nunca** llama a `setInstance`, así que la API no lo emite. *(Alternativa: emitirlo con la URI de la petición. Rechazada: añade runtime sin que nadie lo consuma.)*
- **`traceId` es OPCIONAL**, no requerido: `addExtensions` solo lo añade **si el MDC lo tiene**.
- **El `400` tiene DOS formas** y ambas deben poder representarse: la de **validación** (`ValidationProblem`, con `errors[]`) y la de **JSON malformado** (`ApiProblem` plano, vía `ResponseEntityExceptionHandler`, **sin** `errors[]`). Por eso `errors` es **opcional** dentro del `400`, y `ValidationProblem` extiende a `ApiProblem` en vez de ser un tipo ajeno.

- *Alternativa — que `GlobalExceptionHandler` devuelva estos records en vez de `ProblemDetail`:* eliminaría por construcción la deriva schema↔cuerpo. Pero rediseña el manejo de errores (non-goal). **Rechazada aquí**; es la evolución natural si la deriva llega a doler.

**Riesgo asumido y su mitigación:** los schemas son *solo documentación* y podrían derivar del cuerpo real → **test que fija la forma del cuerpo real** contra los campos documentados. Si alguien cambia el handler, rompe.

### D3 — Los transversales se inyectan con un `OperationCustomizer` (por defecto, no por disciplina)

Repetir `@ApiResponse(401/403/500)` en cada método no escala y se olvidará. Un `OperationCustomizer` es un bean que springdoc aplica a **todas** las operaciones y puede **inspeccionar el `HandlerMethod`**.

| Respuesta | A qué operaciones | Cómo lo decide |
|---|---|---|
| `500` | **todas** | siempre (el catch-all cubre todo) |
| `401` | las que **requieren sesión** | la ruta **no** es pública |
| `403` | las que exigen rol | el `HandlerMethod` lleva **`@PreAuthorize`** |

Cuando `patients` añada `GET /patients` protegido, **hereda `401` y `500` sin escribir una línea**; y con `@PreAuthorize`, también el `403`.

Lo **específico de cada operación** sí se declara en ella (solo el autor lo sabe): el status de éxito, el `400` (donde hay `@Valid`), el `409` (conflicto), y el **`401` semántico** de `login`/`refresh` — que el customizer **no** añade, por ser rutas públicas.

**La lista de rutas públicas se extrae a una fuente única compartida con `SecurityConfig`**, en vez de copiarla: si se duplica, el contrato acabaría documentando un `401` donde nunca ocurre.

- *Alternativa — meta-anotación `@StandardApiErrors` puesta a mano en cada controller:* más explícita, pero **vuelve a depender de que el dev se acuerde**. **Rechazada.**

### D4 — El frontend consume los tipos de error **generados**; ninguna forma de error escrita a mano

Hoy existe literalmente `interface ProblemDetailBody { detail?: string }` en `apiError.ts`: la forma del error **escrita a mano**, que es lo que la capacidad prohíbe. Con los schemas en el contrato, orval genera los tipos y `apiError.ts` pasa a **derivarlos**.

Los formularios mapean `errors[]` → errores por campo de react-hook-form (paridad con la validación Zod, `docs/coding-style.md §7`).

Si orval no expone el tipo de error directamente (el cliente es `axios-functions`; en axios el error viaja en `AxiosError<T>`), se **parametriza con el tipo generado** — sigue siendo *derivado del contrato*. Ver Open Questions.

### D5 — El guardián que el diseño daba por hecho **no existe**: hay que construirlo

*(Corrección: la versión inicial de este design afirmaba que "si alguien añade un endpoint sin regenerar, CI falla". **Es falso.**)*

El job `codegen-drift` solo cubre **contrato → cliente**. La dirección que de verdad importa aquí — **backend → contrato** — **no la vigila nadie**. Sin ella, este change es cosmético: el contrato quedaría fiel hoy y volvería a mentir mañana.

**Decisión (D7 la implementa):** cerrar el círculo con un job de backend. Las dos direcciones quedan cubiertas:

| Dirección | Quién la vigila | Estado |
|---|---|---|
| backend → `contracts/openapi.json` | **job nuevo** (D7) | ⬅️ **lo que falta** |
| `contracts/openapi.json` → cliente generado | `codegen-drift` (frontend) | ✅ ya existe |

### D6 — El `401` de "no autenticado" pasa a RFC 9457 (**único cambio de runtime**)

`SecurityConfig` hace `response.sendError(401)` → cuerpo por defecto del contenedor. Es el **único** error de la API que se sale del formato común.

**Decisión:** el entry point **escribe un `ProblemDetail`** en la respuesta, como el resto. Así el contrato puede documentar **un solo schema de error** para todo.

- *Alternativa — documentar el `401` con su forma real (distinta):* obligaría a **dos schemas de error** en el contrato y a que el frontend ramificara por forma. Perpetúa una inconsistencia que es un **defecto**, no una decisión. **Rechazada.**
- *Alternativa — dejarlo sin documentar:* el contrato callaría sobre el error más común de una API autenticada. **Rechazada.**

Es un cambio de comportamiento **mínimo y de bajo riesgo** (mismo status `401`, solo cambia el cuerpo), pero es real y por eso está declarado en el Impact del proposal. **Sin él, el contrato no puede decir la verdad sobre el `401`.**

> Nota: el `403` **sí** es ya RFC 9457 — pasa por `GlobalExceptionHandler.handleAccessDenied` (añadido al arreglar el bug del `500`). No hay nada que alinear ahí.

### D7 — Job de CI `contract-drift` (backend): el contrato debe reflejar el backend

**Decisión:** un job en el workflow de backend que **arranca la aplicación**, exporta `/v3/api-docs` y **falla si difiere de `contracts/openapi.json`** (mismo patrón que `codegen-drift`, pero en la otra dirección).

- Necesita la app arrancada, así que **reutiliza el PostgreSQL de CI** (los `*IT` ya lo levantan con Testcontainers); alternativamente puede exportar el documento en un test de integración, sin servidor HTTP real. **La vía concreta se elige al implementar** — lo que el spec fija es *que CI lo detecte*, no cómo.
- Con esto **queda satisfecho por fin** el Scenario de drift que la capacidad ya prometía.

## Risks / Trade-offs

- **[springdoc deja el `200` fantasma junto al `201`/`204` declarado]** → Verificación explícita sobre `contracts/openapi.json`; es una tarea, no un supuesto.
- **[Los schemas son "solo documentación" y pueden derivar del cuerpo real]** (consecuencia de D2) → Test que fija la forma real contra lo documentado.
- **[El customizer duplica la lista de rutas públicas de `SecurityConfig`]** → Se extrae a una **fuente única compartida** (D3). Si se duplicara, el contrato documentaría `401` donde nunca ocurre (ruido, no fallo).
- **[Cambiar el cuerpo del `401` rompe a algún consumidor]** (D6) → El status **no cambia**; solo el cuerpo, que hoy **nadie tipa** (el front solo lee `detail`, que pasará a existir). Riesgo muy bajo, y hay un test que lo fija.
- **[El job de drift del backend es lento o frágil]** (arrancar la app en CI) → Si arrancar el servidor resulta frágil, se exporta el documento **desde un test de integración** (sin HTTP), que es más estable. Decisión abierta al implementar.
- **[El diff de regenerar contrato + cliente es enorme]** → Va en un **commit aparte** del código a mano, para que la revisión sea legible.

## Migration Plan

Sin migración de datos. **Un solo cambio de runtime** (el cuerpo del `401`), sin impacto en el status.

1. Backend: alinear el `401` (D6) → schemas de error (D2) → `OperationCustomizer` (D3) → anotaciones de status/errores (D1).
2. Arrancar el backend y regenerar `contracts/openapi.json`; **verificar** que el `200` fantasma desapareció y que los errores están.
3. CI: job `contract-drift` (D7).
4. Frontend: `generate:api`, sustituir `ProblemDetailBody` por los tipos generados, mapear `errors[]` → campo.
5. `mvn verify` + `pnpm validate` + **ambos** jobs de drift en verde.

**Rollback:** revertir el commit. No hay estado persistido.

## Open Questions

1. **¿orval (`client: axios-functions`) expone el schema de error como tipo utilizable, o hay que parametrizar `AxiosError<T>` con el tipo generado?** Se resuelve al regenerar, inspeccionando lo generado; en ningún caso se escribe la forma del error a mano (D4). No bloquea el diseño.
2. **¿Cómo exporta el job de CI el documento: arrancando el servidor o desde un test de integración?** Se decide al implementar (D7); el spec exige *que CI detecte el drift*, no el mecanismo. Preferencia: la vía **más estable** en CI.

> *Cerrada durante la revisión:* el **`409` se documenta como una única respuesta** cuyo `type` discrimina (`email-already-exists` vs `organization-slug-taken`) — el handler ya emite esos `type` distintos, así que no hacen falta dos respuestas y el frontend ramifica por `type`.
