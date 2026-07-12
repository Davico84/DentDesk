## Context

`api-type-contracts` ya está construida: el backend publica `/v3/api-docs`, se versiona en `contracts/openapi.json`, y el frontend genera tipos + funciones con orval (ver `docs/frontend.md`). El backend ya emite errores uniformes en **RFC 9457 (`ProblemDetail`)** desde `GlobalExceptionHandler`, con extensiones `timestamp` y `traceId` (ver `docs/backend.md §10`).

El hueco está **solo en la documentación del contrato**:

- Los controllers devuelven `ResponseEntity` con el status decidido en runtime (`HttpStatus.CREATED`, `NO_CONTENT`). springdoc **no puede inferirlo** y documenta `200` para todo.
- Ninguna operación declara respuestas de error, así que el contrato no las describe y **orval no puede generar sus tipos**.

Restricción dura de este change (viene del proposal): **no se cambia el comportamiento en runtime**. Ni un status ni un cuerpo distinto. Solo se hace que el contrato **diga la verdad**.

Restricción de escala: el proyecto aspira a ser un **starter template SaaS**. Lo que decidamos aquí lo heredarán `users`, `patients`, `appointments`, `billing`… Si documentar errores exige que cada dev recuerde repetir anotaciones en cada método, el contrato volverá a desincronizarse. **La solución tiene que ser por defecto, no por disciplina.**

## Goals / Non-Goals

**Goals:**

- El contrato declara el **status real** de cada operación (`201` registro, `204` logout, `200` resto).
- El contrato declara las **respuestas de error** de cada operación, con **schema tipado** (no un `object` opaco).
- El frontend **deriva** los tipos de error del contrato (cumple el propósito de la capacidad).
- Los errores **transversales** (`401`, `403`, `500`) se aplican **de forma central y automática**: una capacidad futura los hereda sin escribir nada.
- El error de validación expone `errors: [{ field, message }]` para pintar el error **junto a cada campo**.

**Non-Goals:**

- Rediseñar `GlobalExceptionHandler` ni el formato de error (se queda RFC 9457).
- Cambiar cualquier status o cuerpo que ya se devuelve.
- Rediseñar la UX de errores del frontend (toasts, banners). Solo se **tipa** lo que ya se maneja.
- i18n de mensajes ni catálogo de códigos de negocio.

## Decisions

### D1 — El status real se declara con `@ApiResponse`, no con `@ResponseStatus` ni refactorizando los controllers

Los controllers necesitan `ResponseEntity` porque **añaden la cookie `Set-Cookie`** del refresh token (`RefreshCookieFactory`). No podemos sustituirlos por `@ResponseStatus` sin refactorizar la gestión de cabeceras.

**Decisión:** declarar el status con la anotación de documentación `@ApiResponse(responseCode = "201", ...)`.

- *Alternativa A — `@ResponseStatus(HttpStatus.CREATED)` en el método:* springdoc **sí** lo lee, pero en runtime Spring lo **ignora** cuando el método devuelve `ResponseEntity`. Quedaría una anotación que miente sobre lo que hace el código: exactamente la clase de trampa que estamos eliminando. **Rechazada.**
- *Alternativa B — refactorizar los controllers para no usar `ResponseEntity`* (status vía `@ResponseStatus`, cookie vía `HttpServletResponse`): más "idiomático", pero **cambia código de runtime** y viola el non-goal de este change. **Rechazada** (podría reconsiderarse en un change propio).

**Cuidado (verificación obligatoria):** springdoc añade una respuesta `200` **por defecto**. Al declarar `201`, hay que comprobar que el `200` por defecto **desaparece** y no queda un contrato con ambas. Se verifica inspeccionando `contracts/openapi.json` tras regenerar.

### D2 — Dos schemas de error propios y tipados, no el `ProblemDetail` de Spring

El `ProblemDetail` de Spring guarda las extensiones (`timestamp`, `traceId`) en un mapa dinámico vía `setProperty(...)`. springdoc **no las ve**, así que su schema autogenerado sería **incompleto**: el frontend no podría tipar `traceId` ni `errors`.

**Decisión:** definir dos **schemas de documentación** propios en la capa `presentation`:

- **`ApiProblem`** — el problema estándar: `type`, `title`, `status`, `detail`, `instance` (RFC 9457) **+ `timestamp` y `traceId`** (nuestras extensiones).
- **`ValidationProblem`** — el de validación: lo anterior **+ `errors: [{ field, message }]`**.

Se usan como `@Content(schema = @Schema(implementation = ...))` en las respuestas de error.

- *Alternativa — cambiar `GlobalExceptionHandler` para que devuelva estos records en vez de `ProblemDetail`:* eliminaría por construcción cualquier riesgo de deriva entre schema y cuerpo real. Pero **cambia el runtime** del manejo de errores (non-goal). **Rechazada para este change**, pero es la evolución natural si la deriva llega a doler.

**Riesgo asumido y su mitigación:** al ser schemas *solo de documentación*, podrían desincronizarse del cuerpo real. Se mitiga con un **test que fija la forma del cuerpo real** (un error de validación y uno estándar) contra los campos documentados. Si alguien cambia el handler, el test rompe.

### D3 — Los errores transversales se inyectan con un `OperationCustomizer` de springdoc (por defecto, no por disciplina)

Repetir `@ApiResponse(401/403/500)` en cada método no escala y se olvidará. Un `OperationCustomizer` es un bean que springdoc aplica a **todas** las operaciones al construir el documento, y puede **inspeccionar el `HandlerMethod`**.

**Decisión:** un `OperationCustomizer` que añade automáticamente:

| Respuesta | A qué operaciones | Cómo lo decide |
|---|---|---|
| `500` | **todas** | siempre (el catch-all existe para todas) |
| `401` | las que **requieren autenticación** | la ruta no es pública (`/auth/**`, actuator, api-docs son `permitAll` en `SecurityConfig`) |
| `403` | las que exigen un rol | el `HandlerMethod` lleva **`@PreAuthorize`** |

Así, cuando `patients` añada `GET /patients` protegido, **hereda `401` y `500` sin escribir una línea**; y si lleva `@PreAuthorize`, también el `403`.

Lo **específico de cada operación** sí se declara en ella (es información que solo el autor conoce):

- el **status de éxito** (`201`, `204`, `200`),
- el **`400`** de validación (donde hay `@Valid`),
- el **`409`** de conflicto (registro: correo o slug duplicado),
- el **`401` semántico** de `login`/`refresh` (credenciales o refresh inválidos — no es "no autenticado", es "no te creo"), que el customizer **no** añadiría por ser rutas públicas.

- *Alternativa — una meta-anotación `@StandardApiErrors` puesta a mano en cada controller:* más explícita, pero **vuelve a depender de que el dev se acuerde**. Rechazada por el criterio de "por defecto, no por disciplina".

### D4 — El frontend consume los tipos de error **generados**; no se escribe ninguna forma de error a mano

Con los schemas en el contrato, orval genera los tipos (`ApiProblem`, `ValidationProblem`). El frontend:

- **deriva** de ellos su manejo de error en la capa API (`docs/frontend.md`),
- y los formularios mapean `errors[]` → errores por campo de react-hook-form (paridad con la validación Zod del cliente, `docs/coding-style.md §7`).

Si orval no expone el tipo de error de forma directa (el cliente es `axios-functions`, y en axios el error viaja en `AxiosError<T>`), se **parametriza con el tipo generado** — sigue siendo *derivado del contrato*, que es lo que exige la capacidad. Nunca se declara la forma del error a mano. Ver Open Questions.

### D5 — Regenerar contrato y cliente es parte del change, y el CI de drift es el guardián

`contracts/openapi.json` se regenera desde el backend en ejecución y el cliente con `generate:api`. El job de **detección de drift** que ya exige la capacidad (`docs/tooling-setup.md`) es lo que impide que esto se vuelva a desincronizar: si alguien añade un endpoint sin regenerar, CI falla.

## Risks / Trade-offs

- **[springdoc deja el `200` por defecto junto al `201` declarado]** → Verificación explícita en `contracts/openapi.json` tras regenerar; es una tarea del plan, no un supuesto.
- **[Los schemas de error son "solo documentación" y pueden derivar del cuerpo real]** (consecuencia aceptada de D2) → Test que fija la forma del cuerpo real contra los campos documentados; rompe si el handler cambia.
- **[El `OperationCustomizer` decide "ruta pública" duplicando la lista de `SecurityConfig`]** → Si alguien añade una ruta pública y no la refleja, el contrato documentaría un `401` que nunca ocurre (ruido, no un fallo). Mitigación: derivar la lista de un **único sitio compartido** con `SecurityConfig` en vez de copiarla.
- **[El diff de regenerar crece mucho]** (el contrato y los archivos generados del front) → Es ruido esperado y revisable; se commitea **separado** del código a mano para que la revisión sea legible.
- **[Tipar los errores puede empujar a rediseñar la UX]** → Se corta explícitamente por los non-goals: en este change solo se **tipa** lo que ya se maneja.

## Migration Plan

No hay migración de datos ni cambio de runtime: **el despliegue es inocuo**.

1. Backend: schemas de error + `OperationCustomizer` + anotaciones de status/errores por operación.
2. Arrancar el backend y regenerar `contracts/openapi.json`; **verificar** que el `200` fantasma desapareció y que los errores están.
3. Frontend: `generate:api` y pasar el manejo de errores a los tipos generados.
4. `mvn verify` + `pnpm validate` + el job de drift en verde.

**Rollback:** revertir el commit. No hay estado persistido que deshacer.

## Open Questions

1. **¿orval (`client: axios-functions`) expone el schema de error como un tipo utilizable, o hay que parametrizar `AxiosError<T>` con el tipo generado?** Se resuelve en el apply, al regenerar: se inspecciona lo generado y se elige la vía **sin escribir la forma del error a mano** (D4). No bloquea el diseño.
2. **¿El `409` del registro debe distinguir `email-already-exists` de `organization-slug-taken` en el contrato?** El backend ya emite `type`/`errorType` distintos. Propuesta: **documentar un único `409`** cuyo `type` los discrimina (evita duplicar respuestas), y que el frontend ramifique por `type`. Confirmar al implementar el formulario de registro.
