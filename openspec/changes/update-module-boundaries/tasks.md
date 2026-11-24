## 1. Frontend — mapa y regla de lint (scope: front)

- [ ] 1.1 Escribir `eslint.boundaries.test.ts` (Vitest) **primero**: casos de import declarado (sin error), no declarado (error), `core → negocio` (error), `core → core` (sin error) y archivo de test (sin error). Debe fallar antes de tocar la config. Incluir un caso que compruebe que un archivo de `core` recibe **todas** las restricciones (que ningún bloque pisa a otro).
- [ ] 1.2 En `eslint.config.js`, definir el mapa `ALLOWED` con el grafo actual (`auth: [staff]`, `clinical-history: [staff, dental-chart, patients]`, `dental-chart: [staff]`, `home: [patients]`; `core`, `branding`, `patients`, `staff`: `[]`) y generar, **en un solo bucle**, un bloque `no-restricted-imports` por módulo con `files: src/modules/<m>/**/*.{ts,tsx}` disjuntos (**el glob debe terminar en extensión**: sin ella ESLint 10 ignora el archivo y la regla no se aplica), `ignores` de `**/*.test.{ts,tsx}`, `**/*.spec.{ts,tsx}` y `**/test/**`, `patterns` explícitos (`@/modules/<x>` y `@/modules/<x>/**`) y mensaje que apunta a `docs/frontend.md`.
- [ ] 1.3 `core` es una entrada más del mismo bucle (`ALLOWED.core = []`, prohíbe todos los módulos de negocio): **sin bloque aparte** y sin ningún otro bloque con `no-restricted-imports` sobre `src/modules/**`.
- [ ] 1.4 Verificar que no hay imports relativos entre módulos (`../../staff/...`); si aparece alguno, agregar patrón relativo.
- [ ] 1.5 El test usa `new ESLint({ cwd: <frontend>, overrideConfigFile: <eslint.config.js> })` y rutas relativas (`src/modules/patients/components/X.tsx`) con `lintText`; sin opciones obsoletas (`useEslintrc`). `pnpm lint` pasa sobre el código actual **sin tocar `src/`**; `pnpm validate` verde.

## 2. Backend — romper el ciclo `auth ↔ staff` (scope: back)

- [ ] 2.1 Mover `EmailAlreadyExistsException` e `InvalidInvitationException` de `service.auth` a `service.staff` (con `git mv`).
- [ ] 2.2 Ajustar imports en `AuthService` y `AuthServiceTest` (`StaffService` queda en el mismo paquete). Confirmar con `rg` (nombre simple y completo, main y test) que no hay otros usos; el handler de `presentation` no las referencia.
- [ ] 2.3 Verificar que el contrato OpenAPI no cambia (`api-type-contracts`: el snapshot sigue igual) y que los tests de registro/invitación siguen verdes.

## 3. Backend — reglas ArchUnit de dominios (scope: back)

- [ ] 3.1 Crear `architecture/ServiceDomainBoundariesTest` con `the_importer_actually_reads_the_bytecode` (debe contener clases de `service.auth` **y** `service.staff`, no solo "no vacío"). Las reglas se escriben como métodos que reciben el `JavaClasses` a evaluar. Correr bajo JDK 25.
- [ ] 3.2 Regla `slices().matching("com.odontorisas.service.(*)..").should().beFreeOfCycles()`.
- [ ] 3.3 Mapa `ALLOWED` con el grafo actual (`admission`, `auth`, `clinicalhistory`, `consultationnote`, `dentalchart`, `platform`, `staff`, `organization`, `patient`) y una regla por dominio.
- [ ] 3.4 Test que falla si existe un paquete `service.<dominio>` sin entrada en el mapa.
- [ ] 3.5 Pruebas de que las reglas fallan con **fixtures package-private, vacíos y sin estereotipos Spring, en `src/test`, que declaran los paquetes reales** (`com.odontorisas.service.staff.*` / `com.odontorisas.service.auth.*`; una clase en `...architecture` no pertenece a ningún slice): ciclo `staff ↔ auth`, dependencia no declarada y dominio sin entrada en el mapa, importados con `importClasses(...)` y verificando que `check` lanza `AssertionError`.
- [ ] 3.6 `mvn verify` verde.

## 4. Convivencia con `add-appointments` (scope: back)

- [ ] 4.1 Antes de empezar el apply, revisar si `service.appointment` ya está en `dev`.
- [ ] 4.2 **Recalcular el grafo desde la rama integrada**, no copiarlo de este documento. En `apply-appointments`, `AppointmentService` depende de `consultationnote`, `patient` y `staff`. Si `service.appointment` ya está en `dev`, declarar su entrada completa; si no, anotar en el PR que quien llegue segundo declara el dominio (el test de "dominio sin entrada" lo fuerza).
- [ ] 4.3 Confirmar que el mapa no se evalúa contra una lista fija de dominios: un dominio nuevo sin entrada debe **fallar**, no quedar fuera.

## 5. Verificación contra violación real (docs/testing.md §5)

- [ ] 5.1 Frontend: agregar a mano un import `@/modules/staff/...` en `modules/patients/components/` y comprobar que `pnpm lint` **falla**; revertir.
- [ ] 5.2 Frontend: agregar un import de `@/modules/patients` en `modules/core/` y comprobar que falla; revertir.
- [ ] 5.3 Backend: reintroducir un import `service.staff → service.auth` y comprobar que `mvn verify` **falla** nombrando el ciclo; revertir.
- [ ] 5.4 Dejar en el PR la evidencia (mensaje del error) de las tres.

## 6. Documentación (scope: docs)

- [ ] 6.1 `docs/adr/0021-limites-entre-modulos.md` — ya escrito en el propose; revisar que el estado quede "Implementado" al archivar.
- [ ] 6.2 `docs/frontend.md`: sección de límites entre módulos (el mapa, cómo agregar una arista, tests exentos, **honestidad**: sin barrels, `core` depende de `store/routes/layouts`).
- [ ] 6.3 `docs/backend.md`: sección de dependencias entre dominios `service.*`, sin ciclos, el mapa, dónde viven las excepciones compartidas del flujo.
- [ ] 6.4 `docs/testing.md`: corregir la nota histórica de ArchUnit (el pom usa **1.4.1**, no 1.3.0; el guardián del importador protege contra una **regresión futura**, no describe la versión actual); enlazar las reglas nuevas en la lista de reglas de arquitectura y añadir el ciclo `auth ↔ staff` como ejemplo de lo que la regla de prosa no detectaba.
- [ ] 6.5 `docs/vision.md`: al archivar, marcar la extensión de `project-foundation` como construida y enlazar el change archivado.
- [ ] 6.6 `openspec validate update-module-boundaries --strict` y revisar a mano que cada requirement ADDED mide ≤500 caracteres.
