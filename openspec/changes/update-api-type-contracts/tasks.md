## 1. Mensaje de error apto para el usuario (backend)

- [x] 1.1 `GlobalExceptionHandler`: añadir el mapa `HttpStatus → detail en español` (400, 404, 405, 415, 406, 413, **503**) más una constante de mensaje genérico para los statuses no mapeados. El `503` lleva mensaje propio porque el genérico ("no se pudo procesar") es engañoso para un "servicio no disponible, reintentá luego". (D1)
- [x] 1.2 En el override de `handleExceptionInternal`, reemplazar el `detail` del `ProblemDetail` por el del mapa (o el genérico) **antes** de devolverlo, junto a las extensiones que ya añade. Resolver `HttpStatusCode`→`HttpStatus` con `HttpStatus.resolve(statusCode.value())`; si devuelve `null` (status no estándar), usar el genérico — nunca dejar que un `null` rompa el handler. (D1)
- [x] 1.3 Verificar que los `@ExceptionHandler` propios (`handleBusiness`, `handleAuthentication`, `handleAccessDenied`, `handleDataIntegrity`, `handleUncaught`) **no** cambian: no pasan por `handleExceptionInternal` y ya construyen su `detail` curado. (D2)

## 1b. Errores de la capa de seguridad (backend) — D4

- [x] 1b.1 Verificar (con test) que el `401` sin sesión y el `403` de method-security ya salen con `detail` en español (delegan en el resolver / `@ExceptionHandler` propios). Es confirmación, no cambio.
- [x] 1b.2 `SecurityConfig`: configurar el `accessDeniedHandler` para **delegar en el `handlerExceptionResolver`**, igual que ya hace el `authenticationEntryPoint`, de modo que un `403` a nivel de ruta salga en RFC 9457 + español (cierra el hueco latente antes de la primera regla de rol por URL, que traerá `staff`).

## 2. Corrección de documentación desactualizada

- [x] 2.1 `ProblemDetails.java`: corregir el Javadoc que afirma que `instance` no se emite — Spring lo rellena solo al renderizar. (D3)
- [x] 2.2 `ErrorContractIT.java`: corregir el comentario de clase (`API_PROBLEM_FIELDS`) con la misma afirmación, que ya se contradice con el comentario correcto del test de validación en el mismo archivo. (D3)

## 3. Tests backend (uno por Scenario del delta)

- [x] 3.1 `ErrorContractIT`: un error resuelto por el framework (JSON malformado) trae un `detail` en español y **no** el mensaje interno de Spring (`"Failed to read request"`).
- [x] 3.2 `ErrorContractIT`: un status resuelto por el framework y no mapeado cae en el mensaje genérico, sin filtrar texto interno.
- [x] 3.3 Confirmar que los tests existentes de forma de error (`ErrorContractIT`, `GlobalExceptionHandlerTest`) siguen verdes: este change cambia el **contenido** de `detail`, no la forma ni el status.
- [x] 3.4 **Camino de seguridad**: confirmado con test que el `401` sin sesión (`ErrorContractIT`) y el `403` de method-security (`GlobalExceptionHandlerTest`) ya salían con `detail` en español — no era parte del hueco, y ahora queda fijado por test. **Nota honesta:** el `403` a nivel de **URL** (tras 1b.2) no tiene hoy ninguna ruta real restringida por rol para ejercerlo end-to-end (`authorizeHttpRequests` solo tiene `permitAll`/`authenticated`) — no se fabricó una ruta sintética solo para probar el wiring (`docs/CLAUDE.md`: no diseñar para escenarios que no pueden pasar hoy). Su primera cobertura real llega con `staff`, sobre el mismo `securityFilterChain` que aquí se editó.

## 4. Frontend

- [x] 4.1 Sin cambios: `getUserFriendlyError` ya consume `detail` correctamente (`docs/frontend.md §2.6`). Confirmar que no hace falta tocarlo.

## 5. Cierre (al archivar)

- [ ] 5.1 `docs/vision.md`: enlazar el change archivado bajo `api-type-contracts` en "Estado de capacidades".
- [ ] 5.2 `docs/backend.md §10`: dejar constancia de que el `detail` apto para el usuario es una garantía de **todos** los errores, incluidos los del manejador por defecto y los de la capa de seguridad.
- [ ] 5.3 `docs/backend.md §10`: **regla para el futuro** — todo `@ExceptionHandler` nuevo, todo método del framework sobreescrito (p. ej. `handleMethodArgumentNotValid`) y todo punto de extensión de seguridad **queda fuera** del mapa automático de `handleExceptionInternal`; por tanto DEBE proveer su propio `detail` en español vía `ProblemDetails.of(...)`. El mapa solo cubre lo que el padre resuelve y pasa por `handleExceptionInternal`. (D2)
