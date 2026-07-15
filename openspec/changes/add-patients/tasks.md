> ⚠️ **Depende de `update-multi-tenancy`.** El contexto de organización se fija **solo** en cada transacción, así que `patients` **no escribe ni una línea de código de aislamiento**: declara su entidad siguiendo `docs/backend.md §8.1` y el aislamiento **ya está**. Es precisamente lo que ese change viene a garantizar — y esta capacidad es su primera prueba real.

## 1. Backend — la tabla del paciente y su aislamiento (design.md D1, y `docs/backend.md §8.1`)

- [ ] 1.1 Migración **V9**: tabla `patients` con `organization_id BIGINT NOT NULL`, datos personales, contacto, responsable, contacto de emergencia y anamnesis
- [ ] 1.2 **Índice sobre `organization_id`** — es **parte de la política de aislamiento**, no una optimización: sin él RLS filtra con *seq scan* y el listado se degrada con cada clínica nueva
- [ ] 1.3 `ENABLE` + **`FORCE ROW LEVEL SECURITY`** y política `organization_isolation` **sin `missing_ok`** (fallo ruidoso ante consulta sin contexto)
- [ ] 1.4 🚨 **Unicidad `(organization_id, document)`**, **NUNCA** sobre `document` a secas: eso impediría que dos clínicas atendieran a la misma persona (`ADR-0004`). El documento admite `NULL` (un bebé puede no tenerlo). **Índice incondicional, NO parcial sobre activos** (design.md D1): el documento de un paciente dado de baja sigue ocupado → re-registrarlo da `409` (se reactiva su ficha, no se duplica)
- [ ] 1.5 Anamnesis como **listas** (`allergies`, `conditions`, `currentMedication`), no texto libre — `treatments` tendrá que recorrerlas para **alertar**, y un blob no permite alertar de forma fiable (design.md D3)
- [ ] 1.6 Entidad `Patient` con **`@TenantId`** + repositorio. **Sin código de aislamiento propio**: el contexto lo pone `TenantAwareTransactionManager`. 🚨 **Ni siquiera importes `TenantSession`** — una regla de ArchUnit **rompe la build** si `service`/`presentation` dependen de él (`docs/backend.md §8.1.1`). Si un fallo cerrado te tienta a fijar el contexto a mano, el problema está en el cimiento, no aquí

## 2. Backend — reglas de negocio (design.md D2, D4)

- [ ] 2.1 **Menor de edad derivado de la fecha de nacimiento** — `isMinor` **NO es un campo persistido**: un dato declarado se desincroniza el día del cumpleaños
- [ ] 2.2 **Validación condicional**: si el paciente es menor, los datos del responsable pasan a **obligatorios**. No se expresa campo a campo: hace falta validación **a nivel de objeto**
- [ ] 2.3 **La baja es lógica, no borrado** (`status`): la historia clínica tiene retención legal y de ella cuelgan tratamientos, citas y cobros. "Dar de baja" en una clínica es **archivar**
- [ ] 2.4 Servicio de pacientes: alta, consulta, edición, baja y listado. **Todo `@Transactional`** (las variables de RLS son *transaction-local*; fuera de transacción el contexto se pierde). El `organizationId` sale **solo del contexto**, jamás de la petición. 🚨 **El servicio habla su propio tipo de dominio, NO `PatientResponse`/`CreatePatientRequest` de `presentation`** (design.md D6): `LayeredArchitectureTest` **rompe la build** si un servicio depende de un DTO de la API. El controller traduce — patrón ya en el repo: `Branding` + `BrandingController`
- [ ] 2.5 **Tests (Scenarios de alta, menor de edad, anamnesis y baja)**: incluidos el documento duplicado en la misma clínica (→ `409`), el **mismo documento en otra clínica** (→ permitido), el **documento de un paciente dado de baja** (→ `409`, sigue ocupado) y la **edición** a un documento ya existente en la clínica (→ `409`)
- [ ] 2.6 🚨 **Test de aislamiento a nivel de MOTOR (RLS), OBLIGATORIO** (`docs/testing.md`; Scenario "Una consulta solo alcanza a los pacientes propios" y "…que omite el filtro sigue aislada"): entra por el **repositorio de `Patient`** con la app conectada como **rol no-superusuario** — extiende **`AbstractTenantAwareIntegrationTest`**, NO `AbstractIntegrationTest` (con el superusuario del contenedor RLS ni se aplica y el test pasaría en verde con el aislamiento roto). Sigue el patrón de **`AutomaticTenantBindingIT`**: puebla `OrganizationContext` como el filtro y **NUNCA llama a `bind()`** — esa ausencia es el aserto

## 3. Backend — la API (design.md D5, `docs/backend.md §10.2`)

- [ ] 3.1 Controller: alta, consulta, edición, baja y **listado con búsqueda** (por nombre y por documento) + paginación. **`GET` + query params**, NO el método `QUERY` (`ADR-0002`)
- [ ] 3.2 Declarar **solo** lo que la herramienta no infiere: el status de éxito, el `400` de validación y el `409` del documento duplicado. **El `401`/`403`/`500` se inyectan solos** — no escribirlos
- [ ] 3.3 **NO añadir `@PreAuthorize` de rol**: los tres roles gestionan pacientes; basta con estar autenticado en la organización (`docs/domain.md`, matriz de actores)
- [ ] 3.4 **Test de aislamiento a nivel HTTP** (Scenario "No se puede acceder a un paciente de otra clínica ni conociendo su identificador"): la clínica A no ve los pacientes de B en el listado, y **no puede consultarlos ni editarlos ni conociendo su identificador exacto** — responde **`404`** (no `403`), **sin revelar que existen en otra clínica**. Este lo garantiza el filtro de aplicación (`@TenantId`), así que **puede correr sobre la base normal** — es complementario al de motor (2.6), no lo sustituye

## 4. Contrato

- [ ] 4.1 Arrancar el backend y exportar `/v3/api-docs` → `contracts/openapi.json` **con `curl -o`**, nunca canalizándolo por un script (un `curl | python` en Windows lo dejó doble codificado y reventó el guardián)
- [ ] 4.2 Regenerar el cliente del frontend. **El guardián `ContractDriftIT` rompe la build** si el contrato no refleja el backend

## 5. Frontend

- [ ] 5.1 **Listado con búsqueda** (el uso diario de recepción): tabla paginada, búsqueda por nombre o documento
- [ ] 5.2 **Ficha del paciente**: alta y edición, con las secciones de datos personales, contacto, responsable (solo si es menor), emergencia y anamnesis
- [ ] 5.3 **Paridad de la validación condicional** (`docs/coding-style.md §7`): el cliente exige el responsable cuando la fecha indica menor de edad. El servidor es la autoridad: si el cliente se despista, su `400` trae el detalle **por campo** y se pinta junto al input (ya resuelto)
- [ ] 5.4 Tipos y errores **derivados del contrato** (`docs/frontend.md §2.1`) — ninguna forma de error escrita a mano
- [ ] 5.5 **Tests (Vitest + MSW)**: alta válida; menor sin responsable → el error del servidor se pinta junto al campo; la búsqueda filtra

## 6. Documentación de estado (al archivar)

- [ ] 6.1 `docs/domain.md`: consolidar `Patient` como **construida** (campos reales) y actualizar el ER
- [ ] 6.2 `docs/vision.md` + `readme`: marcar `patients` ✅ construida

## 7. Cierre

- [ ] 7.1 `openspec validate add-patients --strict`
- [ ] 7.2 `mvn verify` (backend) y `pnpm validate` (frontend) en verde, **y los dos guardianes de drift**
- [ ] 7.3 **Campos obligatorios del alta** (Open Question 1, ya resuelta en design.md): implementar `fullName` y `birthDate` como únicos obligatorios; documento, contacto, emergencia y anamnesis **opcionales**; responsable obligatorio **solo si es menor**
- [ ] 7.4 Commits por scope. **El contrato regenerado y el cliente van en un commit aparte** del código escrito a mano
