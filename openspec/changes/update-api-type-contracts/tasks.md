## 1. Backend — un único formato de error en toda la API (design.md D6)

- [x] 1.1 `SecurityConfig`: el `authenticationEntryPoint` deja de usar `sendError(401)` (que devuelve el cuerpo por defecto del contenedor) y **escribe un `ProblemDetail` RFC 9457**, con el mismo `type`/`title`/`detail` + `traceId` que el resto de errores
- [x] 1.2 **Test (Scenario "El error de sesión ausente usa el formato común")**: una petición a un recurso protegido **sin token** devuelve `401` **con cuerpo RFC 9457** (no el `{timestamp,status,error,path}` del contenedor). *(Hoy `AuthControllerIT.protected_route_without_token_returns_401` solo comprueba el status: hay que fijar también el cuerpo.)*
- [x] 1.3 Verificar que el `403` de `@PreAuthorize` y el `401` semántico de `login`/`refresh` **ya** son RFC 9457 (no requieren cambio) y dejarlo fijado por test

## 2. Backend — schemas de error tipados y fieles (design.md D2)

- [x] 2.1 `presentation/dto/ApiProblem`: `type`, `title`, `status`, `detail` + `timestamp` y `traceId`. **Sin `instance`** (el handler nunca lo emite) y con **`traceId` opcional** (solo se añade si el MDC lo tiene)
- [x] 2.2 `presentation/dto/ValidationProblem`: extiende lo anterior con **`errors: [{ field, message }]` opcional** — porque el `400` tiene **dos formas**: validación (con `errors[]`) y **JSON malformado** (sin `errors[]`)
- [x] 2.3 **Test (Scenario "El schema de error describe el cuerpo real")**: provocar un error de validación, uno de JSON malformado y uno estándar; fijar que el cuerpo solo contiene campos documentados, que no se documenta nada que la API no emita, y que `traceId` es opcional

## 3. Backend — errores transversales automáticos (design.md D3)

- [x] 3.1 Extraer las **rutas públicas a una fuente única** compartida con `SecurityConfig` (hoy la lista vive solo ahí; duplicarla haría que el contrato documentara `401` donde nunca ocurre)
- [x] 3.2 `OperationCustomizer` de springdoc: añade `500` a **todas** las operaciones; `401` a las que **no** son públicas; `403` a las cuyo `HandlerMethod` lleva `@PreAuthorize`
- [x] 3.3 **Test (Scenario "Los errores transversales se heredan sin declararlos")**: `getBranding` declarará **solo `200`** (tarea 4.5), así que basta con asertar que el documento le añade `401` y `500` — **no hace falta un controller sintético**

## 4. Backend — status real y errores específicos por operación (design.md D1)

- [x] 4.1 `AuthController.register`: **`201`** + `400` (validación) + `409` (con `type` que discrimina correo vs slug)
- [x] 4.2 `AuthController.logout`: **`204`** (sin cuerpo)
- [x] 4.3 `AuthController.login`: `200` + `400` + **`401` semántico** (el customizer no lo añade: es ruta pública)
- [x] 4.4 `AuthController.refresh`: `200` + **`401` semántico**
- [x] 4.5 `BrandingController.getBranding`: **solo `200`** (el `401`/`500` los pone el customizer — y eso es justo lo que verifica 3.3)
- [x] 4.6 `BrandingController.updateBranding`: `200` + `400` (el `401`/`403`/`500` los pone el customizer)
- [x] 4.7 **Verificar el riesgo de D1**: donde se declara `201`/`204`, **el `200` fantasma de springdoc desaparece** (no deben convivir)

## 5. Contrato — regenerar y verificar

- [x] 5.1 Arrancar el backend y exportar `/v3/api-docs` → `contracts/openapi.json`
- [x] 5.2 **Verificar el JSON**: `201` en registro sin `200`; `204` en logout; `400`/`401`/`403`/`409`/`500` donde corresponde; los cuerpos de error referencian los schemas tipados (no `object` opaco)
- [x] 5.3 **Test (Scenario "El status documentado coincide con el real")**: ejecutar cada operación y comparar el status obtenido con el declarado en el contrato versionado

## 6. CI — el guardián que faltaba: drift backend → contrato (design.md D5/D7)

- [x] 6.1 Job `contract-drift` en el workflow de **backend**: obtiene la especificación OpenAPI del backend actual y **falla si difiere de `contracts/openapi.json`**. **Resolver la Open Question 2**: exportarla arrancando el servidor o desde un test de integración (elegir la vía **más estable** en CI)
- [x] 6.2 **Test/verificación (Scenario "Un endpoint nuevo sin regenerar el contrato rompe CI")**: comprobar que el job falla si el backend y el contrato divergen
- [x] 6.3 Retirar del workflow del frontend el comentario que decía que esta verificación estaba pendiente (ya no lo está)

## 7. Frontend — tipos de error derivados del contrato (design.md D4)

- [ ] 7.1 `pnpm --filter odontorisas-frontend generate:api` y **resolver la Open Question 1**: ver si orval expone el tipo de error directamente o hay que parametrizar `AxiosError<T>` con el tipo generado (en ningún caso escribir la forma del error a mano)
- [ ] 7.2 **Eliminar `interface ProblemDetailBody` de `modules/core/utils/apiError.ts`** (la forma del error escrita a mano) y derivarla del tipo generado
- [ ] 7.3 Formularios (login, registro, marca): mapear `errors[]` del servidor → errores **por campo** de react-hook-form (paridad con la validación Zod del cliente)
- [ ] 7.4 **Tests (Vitest + MSW)**: el typecheck pasa con los tipos generados; un `400` con `errors[]` pinta el mensaje **junto al campo**; no queda ninguna forma de error escrita a mano

## 8. Documentación (estándares perennes)

- [ ] 8.1 `docs/backend.md`: **un único formato de error** (incluido el `401` de sesión); convención de documentar el status real y los errores por operación, y **qué NO hay que escribir** en un endpoint nuevo (los transversales son automáticos)
- [ ] 8.2 `docs/frontend.md`: manejo de errores **tipados desde el contrato** (capa API + formularios)
- [ ] 8.3 `docs/coding-style.md §7`: la paridad de validación cliente/servidor incluye el mapeo `errors[]` → campo
- [ ] 8.4 `docs/testing.md`: cómo se testea el contrato (status real, forma del error, herencia de transversales) y los **dos** jobs de drift
- [ ] 8.5 `docs/tooling-setup.md`: documentar el job `contract-drift`

## 9. Documentación de estado (al archivar)

- [ ] 9.1 `docs/vision.md` + `readme`: `api-type-contracts` sigue ✅ construida; reflejar que el contrato ahora documenta status reales y errores tipados, y que **CI vigila las dos direcciones** del drift

## 10. Cierre

- [ ] 10.1 `openspec validate update-api-type-contracts --strict`
- [ ] 10.2 `mvn verify` (backend) y `pnpm validate` (frontend) en verde, **y los DOS jobs de drift en verde** (`contract-drift` + `codegen-drift`)
- [ ] 10.3 Commits por scope, Conventional Commits en español. **El contrato regenerado y los artefactos del codegen van en un commit aparte** del código escrito a mano, para que la revisión sea legible
