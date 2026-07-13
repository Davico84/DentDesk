## 1. ⭐ El enganche del contexto de tenant — PRIMERO, y nada más hasta que esté verde (design.md D1)

> Sin esto, `app.organization_id` nunca se fija y **RLS falla cerrado en toda consulta de pacientes**. Si el enganche no funciona, el resto del change es humo. Se valida **antes** de escribir una línea de CRUD.

- [ ] 1.1 **Resolver la Open Question 1**: implementar el enganche en el punto por el que pasan **todas** las transacciones (candidata preferida: el gestor de transacciones; alternativa: interceptor de Hibernate). Reglas: si hay identidad **y** organización → se fijan ambas; solo identidad → solo esa; ninguna → **nada** (y RLS falla cerrado, que es lo correcto). Usa el `TenantSession.bind(...)` que ya existe
- [ ] 1.2 Comprobar que **los flujos de auth siguen funcionando**: el registro crea la organización en la misma transacción, así que **no hay token del que leer el contexto** — conserva su fijación explícita (Scenario "Los flujos previos a la sesión siguen funcionando")
- [ ] 1.3 **Test de aislamiento con una tabla de negocio real** (Scenario "Una entidad de negocio nueva queda aislada sin código propio"): una tabla con RLS por organización, **sin una línea de código de aislamiento**, queda restringida a la organización de la petición. Contra PostgreSQL real, como el rol de aplicación **no-owner** (es el único contexto donde RLS aplica de verdad)
- [ ] 1.4 **Test fail-closed** (Scenario "Una operación sin contexto falla de forma ruidosa"): sin contexto, la operación **falla**; NO devuelve vacío —un vacío se confundiría con "no hay datos" y ocultaría el fallo—

## 2. Backend — la tabla del paciente y su aislamiento (design.md D2, y `docs/backend.md §8.1`)

- [ ] 2.1 Migración **V9**: tabla `patients` con `organization_id BIGINT NOT NULL`, datos personales, contacto, responsable, contacto de emergencia y anamnesis
- [ ] 2.2 **Índice sobre `organization_id`** — es **parte de la política de aislamiento**, no una optimización: sin él RLS filtra con *seq scan* y el listado se degrada con cada clínica nueva
- [ ] 2.3 `ENABLE` + **`FORCE ROW LEVEL SECURITY`** y política `organization_isolation` **sin `missing_ok`** (fallo ruidoso ante consulta sin contexto)
- [ ] 2.4 🚨 **Unicidad `(organization_id, document)`**, **NUNCA** sobre `document` a secas: eso impediría que dos clínicas atendieran a la misma persona (`ADR-0004`). El documento admite `NULL` (un bebé puede no tenerlo)
- [ ] 2.5 Anamnesis como **listas** (`allergies`, `conditions`, `currentMedication`), no texto libre — `treatments` tendrá que recorrerlas para **alertar**, y un blob no permite alertar de forma fiable (design.md D4)
- [ ] 2.6 Entidad `Patient` con **`@TenantId`** + repositorio

## 3. Backend — reglas de negocio (design.md D3, D5)

- [ ] 3.1 **Menor de edad derivado de la fecha de nacimiento** — `isMinor` **NO es un campo persistido**: un dato declarado se desincroniza el día del cumpleaños
- [ ] 3.2 **Validación condicional**: si el paciente es menor, los datos del responsable pasan a **obligatorios**. No se expresa campo a campo: hace falta validación **a nivel de objeto**
- [ ] 3.3 **La baja es lógica, no borrado** (`status`): la historia clínica tiene retención legal y de ella cuelgan tratamientos, citas y cobros. "Dar de baja" en una clínica es **archivar**
- [ ] 3.4 Servicio de pacientes: alta, consulta, edición, baja y listado. **Todo `@Transactional`** (las variables de RLS son *transaction-local*; fuera de transacción el contexto se pierde). El `organizationId` sale **solo del contexto**, jamás de la petición
- [ ] 3.5 **Tests (Scenarios de alta, menor de edad, anamnesis y baja)**: incluidos el documento duplicado en la misma clínica (→ conflicto) y el **mismo documento en otra clínica** (→ permitido)

## 4. Backend — la API (design.md D6, `docs/backend.md §10.2`)

- [ ] 4.1 Controller: alta, consulta, edición, baja y **listado con búsqueda** (por nombre y por documento) + paginación. **`GET` + query params**, NO el método `QUERY` (`ADR-0002`)
- [ ] 4.2 Declarar **solo** lo que la herramienta no infiere: el status de éxito, el `400` de validación y el `409` del documento duplicado. **El `401`/`403`/`500` se inyectan solos** — no escribirlos
- [ ] 4.3 **NO añadir `@PreAuthorize` de rol**: los tres roles gestionan pacientes; basta con estar autenticado en la organización (`docs/domain.md`, matriz de actores)
- [ ] 4.4 **Test de aislamiento a nivel HTTP** (Scenarios de invisibilidad entre clínicas): la clínica A no ve los pacientes de B en el listado, y **no puede consultarlos ni editarlos ni conociendo su identificador exacto** — debe responder "no encontrado", **sin revelar que existen en otra clínica**

## 5. Contrato

- [ ] 5.1 Arrancar el backend y exportar `/v3/api-docs` → `contracts/openapi.json` **con `curl -o`**, nunca canalizándolo por un script (un `curl | python` en Windows lo dejó doble codificado y reventó el guardián)
- [ ] 5.2 Regenerar el cliente del frontend. **El guardián `ContractDriftIT` rompe la build** si el contrato no refleja el backend

## 6. Frontend

- [ ] 6.1 **Listado con búsqueda** (el uso diario de recepción): tabla paginada, búsqueda por nombre o documento
- [ ] 6.2 **Ficha del paciente**: alta y edición, con las secciones de datos personales, contacto, responsable (solo si es menor), emergencia y anamnesis
- [ ] 6.3 **Paridad de la validación condicional** (`docs/coding-style.md §7`): el cliente exige el responsable cuando la fecha indica menor de edad. El servidor es la autoridad: si el cliente se despista, su `400` trae el detalle **por campo** y se pinta junto al input (ya resuelto)
- [ ] 6.4 Tipos y errores **derivados del contrato** (`docs/frontend.md §2.1`) — ninguna forma de error escrita a mano
- [ ] 6.5 **Tests (Vitest + MSW)**: alta válida; menor sin responsable → el error del servidor se pinta junto al campo; la búsqueda filtra

## 7. Documentación (estándares perennes)

- [ ] 7.1 `docs/backend.md §8.1`: documentar que el contexto de tenant **se fija solo** y **qué NO hay que escribir** en una capacidad de negocio nueva (es el equivalente al `OperationCustomizer` de los errores: por defecto, no por disciplina)
- [ ] 7.2 `docs/testing.md`: el test de aislamiento como **puerta de entrada** de toda capacidad de negocio

## 8. Documentación de estado (al archivar)

- [ ] 8.1 `docs/domain.md`: consolidar `Patient` como **construida** (campos reales) y actualizar el ER
- [ ] 8.2 `docs/vision.md` + `readme`: marcar `patients` ✅ construida

## 9. Cierre

- [ ] 9.1 `openspec validate add-patients --strict`
- [ ] 9.2 `mvn verify` (backend) y `pnpm validate` (frontend) en verde, **y los dos guardianes de drift**
- [ ] 9.3 **Resolver la Open Question 2**: confirmar los campos obligatorios del alta (`fullName` y `birthDate`; el documento **opcional**)
- [ ] 9.4 Commits por scope. **El contrato regenerado y el cliente van en un commit aparte** del código escrito a mano
