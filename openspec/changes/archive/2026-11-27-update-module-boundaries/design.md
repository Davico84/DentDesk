## Context

Auditoría de imports (2026-11-24, sobre `dev` en `06d6c1d`; **recalculada sobre `origin/dev` en `895f11c`**, con `add-appointments` integrado). La base de comparación es siempre `origin/dev`, no una rama `dev` local que puede estar atrasada. Lo que **existe** y lo que **no**:

**Frontend (`modules/frontend/src/modules/*`)**

| Módulo | Importa de otros módulos de negocio (código de producción) |
|---|---|
| `core` | ninguno ✅ (pero sí de `@/store`, `@/routes/paths` y `@/layouts/RootLayout`: no son módulos) |
| `auth` | `staff` (1: `ProfessionalCredentialSection` en `ProfilePage`) |
| `branding`, `staff` | ninguno |
| `patients` | ninguno (solo un `*.test.tsx` importa `auth`) |
| `dental-chart` | `staff` (1: `SignedByLine`); algunos tests importan de sí mismo |
| `clinical-history` | `staff` (8), `dental-chart` (7), `appointments` (2: el enlace "agendar la próxima cita" en `OpenNoteEditor` y `SealedNoteCard`), `patients` (1) |
| `appointments` | `staff` (2: odontólogos agendables), `patients` (1: buscador de pacientes) |
| `home` | `patients` (1: `listNavigation`) |

Sin ciclos hoy (`clinical-history → appointments` es de una sola dirección: la agenda no importa de la historia). Sin barrels (`index.ts`): se importa por ruta profunda (`@/modules/staff/hooks/useProfessionalCredential`). ESLint: solo `react/no-multi-comp`.

**Backend (`com.odontorisas.service.*`)**: ArchUnit vigila capas, tenancy, `@Async`/`@Scheduled` y el sellado coordinado. Grafo real entre paquetes `service.*`:

```
admission        → clinicalhistory, dentalchart, staff
appointment      → consultationnote, patient, staff
auth             → organization, staff
clinicalhistory  → patient
consultationnote → clinicalhistory, patient, staff
dentalchart      → patient
platform         → auth, organization, staff
staff            → auth          ← CICLO con auth → staff
organization, patient → (nada)
```

`dentalchart` menciona `clinicalhistory.ClinicalHistoryService` solo en un `{@link}` de Javadoc: no deja dependencia en el bytecode y ArchUnit no la ve, así que **no** es una arista. `ConsultationNoteService` usa `AppointmentRepository` (bloqueo del descarte), que es `persistence`, no un dominio `service`: tampoco es arista.

El único ciclo es `auth ↔ staff`. `StaffService` solo usa de `auth` dos excepciones (`EmailAlreadyExistsException`, `InvalidInvitationException`), ambas del flujo de invitación.

## Goals / Non-Goals

**Goals**
- Que el grafo actual de dependencias entre módulos/dominios quede **declarado en un solo lugar por lado** y **vigilado por la build**.
- Romper el único ciclo real.
- Documentar con honestidad: qué se vigila, qué no, y que las dependencias existentes son **aceptadas**, no ideales.

**Non-Goals**: ver `proposal.md` (sin barrels, sin rediseñar dependencias, sin vigilar `presentation`/`infra`/`screens`/`routes`/`store`).

## Decisions

### D1. Frontend: `no-restricted-imports` nativo con un mapa en `eslint.config.js`
Un objeto `ALLOWED = { appointments: ["patients", "staff"], auth: ["staff"], "clinical-history": ["appointments", "staff", "dental-chart", "patients"], "dental-chart": ["staff"], home: ["patients"], core: [], branding: [], patients: [], staff: [] }`. Para cada módulo se genera un bloque `files: ["src/modules/<m>/**/*.{ts,tsx}"]` con `patterns` que prohíben `@/modules/<otro>` y `@/modules/<otro>/**` para todo `otro` ∉ `ALLOWED[m] ∪ {m, core}`. `core` es **una entrada más del mismo mapa** (`ALLOWED.core = []`): prohíbe todos los de negocio en su propio bloque generado, **no** en un bloque aparte. El mensaje del error nombra la regla y apunta a `docs/frontend.md`.

- ⚠️ **Los bloques NO pueden solaparse.** En flat config, si un archivo cae en dos objetos con `no-restricted-imports`, **el último reemplaza al primero** (las `patterns` no se acumulan; ESLint 10.6.0 según `pnpm-lock.yaml`). Por eso: un bloque por módulo, con `files: ["src/modules/<m>/**/*.{ts,tsx}"]` disjuntos, generados en un solo bucle, y **ningún otro bloque** del config configura `no-restricted-imports` sobre `src/modules/**`. Un test (task 1.1) lo vigila: un archivo de `core` debe recibir **todas** las restricciones.
- Las `patterns` son explícitas por módulo (`@/modules/staff` y `@/modules/staff/**`); no se usa un único `@/modules/*` esperando que sea recursivo.

- **Una arista nueva = una línea en el mapa**, visible en el diff y en la revisión. Ese es el punto: no impedir dependencias, sino que **no entren sin que nadie las vea dentro del alcance de la regla** (imports estáticos con alias `@/modules/...` entre módulos; ver Risks).
- Cero dependencias nuevas.
- ⚠️ **El glob de `files` debe terminar en extensión.** Verificado empíricamente con ESLint 10.6.0: `files: ["src/modules/<m>/**"]` responde *"File ignored because no matching configuration was supplied"* y la regla **no se aplica** (tampoco con `lintText` y un `filePath` inexistente); `src/modules/<m>/**/*.{ts,tsx}` sí. También verificado: `patterns` con `@/modules/staff` y `@/modules/staff/**` detectan import exacto y subruta; dos bloques solapados → el segundo reemplaza las `patterns` del primero; bloques disjuntos → independientes.
- **Tests exentos** (`ignores: ["**/*.test.{ts,tsx}", "**/*.spec.{ts,tsx}", "**/test/**"]`): un test de integración legítimamente arma varios módulos (hoy 4 archivos `*.test.*` lo hacen) y los helpers de `src/modules/*/test/` son soporte de pruebas (hoy no cruzan módulos). Se documenta como excepción, igual que `core/ui` en `no-multi-comp`. La exención aplica **también a `core`**; el requirement lo dice.
- **Módulo nuevo sin entrada = error.** El test lista los directorios reales de `src/modules/` y exige que coincidan con las claves de `ALLOWED` (sobrantes y faltantes). Sin esto, un módulo nuevo quedaría sin bloque generado y sin vigilancia; es el mismo control que D2.3 hace en el backend.
- **Código generado por orval** (`src/modules/core/services/generated`): sigue en `globalIgnores` (se valida con `tsc`, no con ESLint; se regenera desde el contrato). Hoy solo importa con rutas relativas dentro de `core` (`./model`, `../../config/httpClient`). Para que la promesa de `core` no tenga un agujero silencioso, el mismo test **recorre esos archivos y falla si alguno importa de `@/modules/<x>` con `x ≠ core`** (verificación aparte, sin sacarlo del ignore).
- **Solo alias `@/modules/...`.** El proyecto importa así; no hay imports relativos entre módulos (verificado: `0` coincidencias). Si aparecieran, la task 1.4 los cubre con un patrón relativo.

### D2. Backend: ArchUnit con `slices()` + mapa de aristas permitidas
En `architecture/ServiceDomainBoundariesTest` (nueva), importando solo producción como `LayeredArchitectureTest`:
1. `slices().matching("com.odontorisas.service.(*)..").should().beFreeOfCycles()`.
2. Un `Map<String, Set<String>> ALLOWED` con el grafo de arriba; por cada slice, `noClasses().that().resideInAPackage("..service.<d>..").should().dependOnClassesThat().resideInAnyPackage(<todos los demás service.* no permitidos>)`.
3. Un test que **falla si existe un paquete `service.<d>` sin entrada en el mapa** (evita que un dominio nuevo nazca sin declarar).
4. `the_importer_actually_reads_the_bytecode` propio (patrón de `LayeredArchitectureTest`): la regla no puede quedar verde por estar vacía. **Comprueba específicamente** que el importador trajo clases de `service.auth` y `service.staff` (los dos extremos del ciclo que se rompe), no solo que la colección global no es vacía. ArchUnit del pom es **1.4.1** sobre Java 25; el guardián ya no cubre un defecto presente sino una **regresión futura** del importador.

Las reglas se escriben como métodos que **reciben el `JavaClasses` a evaluar** (no un campo fijo), para poder correrlas sobre fixtures en D4.

Las clases sueltas directamente en `service` (`BusinessException`) no pertenecen a ningún slice y **no se vigilan**; las reglas vigilan **dominios**, no todo `service.*`.

### D3. Romper el ciclo moviendo las dos excepciones a `service.staff`
`InvalidInvitationException` pertenece sin duda al **flujo de invitación** (`staff`). `EmailAlreadyExistsException` es **más ambigua**: la lanzan `AuthService` (registro, `AuthService.java:87`) **y** `StaffService` (invitación/alta de miembro). Se mueve a `service.staff` **por la dirección del grafo, no por pertenencia conceptual pura**: `AuthService` ya depende de `StaffService`, así que `auth → staff` se mantiene y desaparece la inversa. Esa justificación queda escrita en el ADR en vez de presentarla como "la excepción es de staff".
- Verificado (2026-11-24): las referencias reales son `AuthService` (:87), `StaffService` (:16-17 y usos), las dos clases mismas y `AuthServiceTest` (:139). **Ni el handler de excepciones de `presentation`, ni snapshots OpenAPI, ni otros servicios las referencian.**
- **Alternativa descartada**: un paquete transversal de excepciones de dominio bajo `service`. Evitaría que `auth` conozca un detalle de `staff`, pero sacaría una excepción de su dominio y crearía una "bolsa de excepciones" sin dueño; hoy el problema es un ciclo de dos paquetes, no la ubicación de dos clases. Se reevalúa si aparece un tercer consumidor.
- Es un cambio de paquete: sin cambio de comportamiento ni de contrato HTTP. `AuthService` y `AuthServiceTest` pasan a importarlas de `service.staff`; `StaffService` deja de importarlas (mismo paquete).
- **Alternativa descartada**: congelar el ciclo con `FreezingArchRule`. Dejaría la regla en verde sobre el defecto que motivó el change.

### D4. Cada regla se prueba fallando (`docs/testing.md §5`)
- **Frontend:** `eslint.boundaries.test.ts` (Vitest) instancia `ESLint` con la config real y usa `lintText` con un `filePath` falso (`src/modules/patients/components/X.tsx` importando `@/modules/staff/...`); espera el error de `no-restricted-imports`. Casos: arista declarada (sin error), no declarada (error), `core → negocio` (error), archivo de test (sin error).
- **Backend:** las reglas (métodos que reciben `JavaClasses`, D2) se ejecutan también contra **fixtures sintéticos** que **declaran los paquetes reales** (`com.odontorisas.service.staff.<Fixture>` y `com.odontorisas.service.auth.<Fixture>`, en `src/test`; **package-private, sin estereotipos Spring** —`@Component`, `@Service`, `@Configuration`, `@Bean`— y sin lógica: clases vacías que solo declaran la dependencia), porque una clase en `...architecture` no pertenece a ningún slice y no podría simular el ciclo. Se importan con `importClasses(...)` y se comprueba que `check` lanza `AssertionError` (patrón de `AdmissionSealingArchitectureTest`). Casos: ciclo `staff ↔ auth`, dependencia no declarada, dominio sin entrada en el mapa. Los fixtures están en `src/test`, así que el importador de producción (`DO_NOT_INCLUDE_TESTS`) no los ve y no contaminan las demás reglas.
- Al aplicar, además se introduce a mano una violación real, se ve romper la build y se revierte (checklist de la task 5).

### D5. Se documenta el estado real, no uno más estricto
`docs/frontend.md` y `docs/backend.md` listan las aristas aceptadas, dicen que **no hay barrels ni API pública por módulo**, y que `core` depende de `@/store`, `@/routes/paths` y `@/layouts/RootLayout`. La regla no promete desacoplamiento: promete **dependencias declaradas y sin ciclos**.

## Risks / Trade-offs

- ⚠️ **El mapa puede volverse ritual**: alguien agrega la arista "para que pase". Mitigación: aparece en el diff, y `docs/frontend.md` pide justificar en el PR toda arista nueva. No es un candado, es visibilidad.
- ⚠️ **ESLint no ve dependencias de tipos puras ni imports dinámicos construidos**. Aceptado: el proyecto usa imports estáticos con alias.
- ⚠️ **ArchUnit ve dependencias de bytecode**, así que una referencia por tipo genérico o anotación cuenta; un `String` con un nombre de clase, no. Aceptado.
- ⚠️ **Alcance real de "no entra sin verse"**: solo cubre imports estáticos entre módulos con alias en el frontend, y dependencias de bytecode entre paquetes `service.<dominio>` en el backend. No cubre imports relativos entre módulos (si aparecieran, task 1.4), imports dinámicos, `screens/`, `routes/`, `layouts/`, `store/`, `presentation`, `infra` ni las clases sueltas de `service`.
- ⚠️ **El grafo puede moverse entre el propose y el apply.** `add-appointments` ya se integró y está en el mapa; si otro change toca imports entre módulos antes del apply, la task 4 recalcula el grafo sobre `dev` y el test de "dominio sin entrada" (D2.3) lo fuerza en el backend.

## Alternativas descartadas

- **`eslint-plugin-boundaries`**: expresa el mismo mapa en una sintaxis de tipos de elemento + resolvers; agrega una dependencia y configuración de resolución de alias. Este proyecto ya sufrió un plugin de ESLint que se rompió con ESLint 10 (`react`, ver `eslint.config.js`). Para 9 módulos, un mapa y `no-restricted-imports` bastan.
- **`dependency-cruiser`**: herramienta aparte con su propio script y CI. Más potente (ciclos en todo el árbol), desproporcionada hoy: el frontend **no tiene ciclos** y el mapa ya vigila lo que importa.
- **Barrels (`index.ts`) y API pública por módulo**: restringiría el import a un punto de entrada. Es un rediseño del frontend con decenas de imports a reescribir y sin dolor actual que lo justifique. Se puede añadir después **encima** de este mapa; no lo contradice.
- **Prohibir toda dependencia entre módulos de negocio**: falso. `clinical-history` necesita la credencial clínica de `staff` (ADR-0016, ADR-0020). Prohibirla obligaría a mover código a `core` que no lo merece (§4.1: "¿lo usaría otra capacidad?").
- **Solo documentar la convención**: es el estado actual, y lo que `docs/testing.md §5` ya enseña que no funciona.
