## 1. Backend — schemas de error tipados (design.md D2)

- [ ] 1.1 `presentation/dto/ApiProblem`: schema del problema estándar (RFC 9457: `type`, `title`, `status`, `detail`, `instance`) + las extensiones reales `timestamp` y `traceId`, con `@Schema` y ejemplos
- [ ] 1.2 `presentation/dto/ValidationProblem`: lo anterior + `errors: [{ field, message }]` (el detalle por campo que emite `handleMethodArgumentNotValid`)
- [ ] 1.3 **Test (Scenario "El schema de error describe el cuerpo real")**: provocar un error de validación y uno estándar, y fijar que el cuerpo devuelto solo contiene campos descritos por los schemas (y que no se documenta nada que la API no emita)

## 2. Backend — errores transversales automáticos (design.md D3)

- [ ] 2.1 Extraer las **rutas públicas a una fuente única** compartida con `SecurityConfig` (hoy la lista vive solo ahí; duplicarla haría que el contrato documentara `401` donde nunca ocurre — riesgo del design)
- [ ] 2.2 `infra/config/OpenApiErrorCustomizer` (`OperationCustomizer` de springdoc): añade `500` a **todas** las operaciones; `401` a las que **no** son públicas; `403` a las cuyo `HandlerMethod` lleva `@PreAuthorize`
- [ ] 2.3 **Test (Scenario "Los errores transversales se heredan sin declararlos")**: una operación protegida que **no** declara errores aparece igualmente con `401` y `500` en el documento; si lleva `@PreAuthorize`, también con `403`

## 3. Backend — status real y errores específicos por operación (design.md D1)

- [ ] 3.1 `AuthController.register`: declarar **`201`** + `400` (validación) + `409` (correo o slug duplicado)
- [ ] 3.2 `AuthController.logout`: declarar **`204`** (sin cuerpo)
- [ ] 3.3 `AuthController.login`: `200` + `400` + **`401` semántico** (credenciales inválidas; el customizer no lo añade por ser ruta pública)
- [ ] 3.4 `AuthController.refresh`: `200` + **`401` semántico** (refresh ausente/inválido)
- [ ] 3.5 `BrandingController.getBranding`: `200` (el `401` y el `500` los pone el customizer)
- [ ] 3.6 `BrandingController.updateBranding`: `200` + `400` (el `401`/`403`/`500` los pone el customizer)
- [ ] 3.7 **Verificar el riesgo de D1**: comprobar que donde se declara `201`/`204` **desaparece el `200` por defecto** de springdoc (no deben convivir)

## 4. Contrato — regenerar y verificar

- [ ] 4.1 Arrancar el backend y exportar `/v3/api-docs` → `contracts/openapi.json`
- [ ] 4.2 **Verificar el JSON** (Scenarios de status y de errores): `201` en registro sin `200`; `204` en logout; `400`/`401`/`403`/`409`/`500` donde corresponde; los cuerpos de error referencian los schemas tipados (no `object` opaco)
- [ ] 4.3 **Test (Scenario "El status documentado coincide con el real")**: ejecutar cada operación y comparar el status obtenido con el declarado en el contrato versionado

## 5. Frontend — tipos de error derivados del contrato (design.md D4)

- [ ] 5.1 `pnpm --filter odontorisas-frontend generate:api` y **resolver la Open Question 1**: inspeccionar si orval expone el tipo de error de forma directa o hay que parametrizar `AxiosError<T>` con el tipo generado (en ningún caso escribir la forma del error a mano)
- [ ] 5.2 Capa API: el manejo de errores se apoya en los **tipos generados**; eliminar cualquier declaración de la forma del error escrita a mano
- [ ] 5.3 Formularios (login, registro, marca): mapear `errors[]` del servidor → errores **por campo** de react-hook-form (paridad con la validación Zod del cliente)
- [ ] 5.4 **Tests (Vitest + MSW)**: el typecheck pasa usando los tipos generados; un `400` con `errors[]` pinta el mensaje **junto al campo**; no queda ninguna forma de error escrita a mano

## 6. Documentación (estándares perennes)

- [ ] 6.1 `docs/backend.md`: convención de documentar el **status real** y los errores por operación, y que los **transversales son automáticos** (qué NO hay que escribir en un endpoint nuevo)
- [ ] 6.2 `docs/frontend.md`: manejo de errores **tipados desde el contrato** (capa API + formularios)
- [ ] 6.3 `docs/coding-style.md §7`: la paridad de validación cliente/servidor incluye el mapeo `errors[]` → campo

## 7. Documentación de estado (al archivar)

- [ ] 7.1 `docs/vision.md` + `readme`: `api-type-contracts` sigue ✅ construida; reflejar que el contrato ahora documenta status reales y errores tipados

## 8. Cierre

- [ ] 8.1 `openspec validate update-api-type-contracts --strict`
- [ ] 8.2 `mvn verify` (backend) y `pnpm validate` (frontend) en verde, **y el job de detección de drift del contrato en verde**
- [ ] 8.3 **Resolver la Open Question 2**: confirmar que el `409` se documenta como una única respuesta cuyo `type` discrimina (`email-already-exists` vs `organization-slug-taken`) y que el registro del frontend ramifica por ese `type`
- [ ] 8.4 Commits por scope, Conventional Commits en español. **El contrato regenerado y los artefactos del codegen van en un commit aparte** del código escrito a mano, para que la revisión sea legible (design.md, riesgo del diff)
