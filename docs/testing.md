# Guía de Pruebas — OdontoRisas

> **Propósito.** Definir la estrategia, estándares y reglas de pruebas del monorepo (backend + frontend).
> Cada capacidad, corrección o refactor se evalúa para elegir el nivel de prueba apropiado.
> Objetivo: **máxima confianza** en la estabilidad con el **mínimo coste** de mantenimiento.

Complementa a `docs/architecture.md` (stack) y a `openspec/` (specs). Aquí va el *cómo* se prueba; el *qué* se prueba nace de los **Scenarios** de cada spec.

---

## 0. Puente OpenSpec → Pruebas (regla central)

En este proyecto las pruebas **derivan de los `#### Scenario:` del spec**. No se inventan casos: se traducen escenarios.

- **Cada `#### Scenario:` (WHEN/THEN) ⇒ al menos un test.** El `WHEN` es el *arrange/act*; el `THEN`/`AND` son las *assertions*.
- El nombre del test **debe referenciar el scenario** para trazabilidad. Ej.: scenario "Migración inicial en base limpia" ⇒ `should_apply_flyway_migrations_on_clean_database` (back) o `it("aplica migraciones Flyway en base limpia", …)`.
- El **nivel** de la prueba (unit/componente/integración/e2e/slice) se elige con las matrices de §3 (front) y §5 (back) — siempre el nivel **más bajo** que valide el comportamiento con fidelidad.
- Un requirement se considera **cubierto** cuando todos sus scenarios tienen prueba verde. Esto es parte de la Definición de "listo".
- Al hacer `/opsx:apply`, las tareas de test de `tasks.md` deben mapear 1:1 (o más) con los scenarios del `spec.md` del change.

> Regla práctica: si un scenario no se puede convertir en una prueba automática, probablemente está mal redactado (ambiguo o no observable) → corrige el spec antes de codear.

---

## 1. Filosofía

Prioriza siempre el **nivel más bajo** capaz de validar el comportamiento.

```
Unit → Componente/Slice → Integración → E2E
```
- No escribas E2E si una prueba de Integración basta. No escribas Integración si una Unitaria basta.
- Principios **F.I.R.S.T.**: Fast, Independent, Repeatable (sin *flaky*), Self-validating, Timely.
- Patrón **AAA** (Arrange–Act–Assert) en todos los niveles.
- Probar **comportamiento**, no detalles de implementación.

---

## 2. Stack de pruebas

| Ámbito | Nivel | Herramientas |
| :--- | :--- | :--- |
| Frontend | Unit / Componente / Integración | Vitest + React Testing Library + MSW |
| Frontend | E2E | Playwright |
| Backend | Unit | JUnit 5 + Mockito |
| Backend | Slices | `@WebMvcTest`, `@DataJpaTest`, `@WebMvcTest`+security, `@SpringBootTest` (websocket) |
| Backend | Integración / persistencia | **Testcontainers (PostgreSQL)** + Flyway |

Decisión del proyecto: **Testcontainers desde el inicio** (PostgreSQL real efímero), no H2 — garantiza paridad con producción. Requiere **Docker** local y en CI.

---

## 3. Frontend — matriz de decisiones

| Si el cambio afecta a… | Nivel |
| :--- | :--- |
| Funciones puras, helpers, formatters, mappers, validators | Unit |
| Zustand stores (acciones y lógica de estado) | Unit |
| Custom hooks lógicos puros | Unit |
| Componentes de UI reutilizables (botones, inputs) | Componente |
| Formularios, modales, tablas aisladas | Componente |
| Custom hooks de React Query (petición, caché) | Integración |
| Comunicación con la API (MSW) | Integración |
| Navegación y enrutamiento (Router) | Integración |
| Flujo de autenticación (login/logout) | Integración + E2E |
| Permisos por rol | Integración + E2E |
| Workflows CRUD completos | E2E |

### Utilidades del proyecto (frontend)
- **`renderWithProviders`** (`src/test/utils.tsx`): envuelve con `MemoryRouter` + `QueryClientProvider` (con `retry: false`) + sesión + i18n. Úsalo en vez del `render` básico cuando el componente dependa de router, caché o sesión.
- **MSW** (`src/test/setup.ts`, `src/test/mocks/{handlers,server}.ts`): intercepta la red a nivel de servicio. Config `onUnhandledRequest: "error"` → toda petición no mockeada falla la prueba. Sobrescribe por test con `server.use(...)`.
- **Zustand**: limpia estado antes de cada test (`localStorage.clear()` + `useXStore.setState(estadoInicial)`), para evitar contaminación entre tests.

### Reglas de oro (RTL)
- Queries por prioridad: `getByRole`/`findByRole` → `getByLabelText` → `getByPlaceholderText` → `getByText`. Evita `querySelector`, `getElementById` y selección por clases CSS. `data-testid` solo como último recurso.
- Usa `@testing-library/user-event` (`const user = userEvent.setup()`), no `fireEvent`.
- Asíncrono: consultas `find*` + `await`, o `await waitFor(() => …)`. **Nunca** `setTimeout`/sleeps.
- No pruebes estilos puramente visuales (colores, márgenes, clases Tailwind) salvo que representen estado de negocio.

### Integración: hook de React Query + MSW
Usa `renderHook` con un `wrapper` que provea un `QueryClient` nuevo por test (con `retry: false`) + MSW + `waitFor` para esperar a que la query resuelva. Si el hook depende de sesión/router, usa los mismos providers que `renderWithProviders`.

---

## 4. Frontend — E2E (Playwright)

- Specs en `modules/frontend/e2e/`, **excluidos de Vitest**.
- Comandos: `pnpm test:e2e` (Chromium) · `pnpm test:e2e:ui` (interactivo). Primera vez: `pnpm exec playwright install chromium`.
- `playwright.config.ts` levanta el dev server y lo reutiliza si ya corre. Para entorno desplegado: `E2E_BASE_URL=... pnpm test:e2e`.
- **Pendiente** para tratar E2E como red de seguridad real: estrategia de datos/entorno (usuario de prueba o seed reproducible) + wiring en CI (navegadores, `forbidOnly`, artefactos). Hasta entonces, los flujos *E2E* de la matriz se cubren **además** con Integración (MSW).

---

## 5. Backend — matriz y slices

| Si el cambio afecta a… | Nivel / Anotación |
| :--- | :--- |
| Lógica de dominio, services, mappers, validadores | Unit (JUnit + Mockito) |
| Controllers (request/response, validación, status) | `@WebMvcTest` (service mockeado) |
| Seguridad / autorización por rol | `@WebMvcTest` + `spring-security-test` |
| Repositorios / `Specification` / queries | `@DataJpaTest` + **Testcontainers** |
| Migraciones Flyway | Integración + **Testcontainers** |
| WebSocket/STOMP | `@SpringBootTest` (websocket) |
| Flujo completo (controller→service→repo→BD) | `@SpringBootTest` + **Testcontainers** |

### Testcontainers (base de integración)
- Un único contenedor PostgreSQL reutilizable para los tests de integración/persistencia; conectar con `@ServiceConnection` (Spring Boot 3.1+/4) para que Spring configure el datasource automáticamente.
- Perfil de test que **no** desactiva Flyway: las migraciones de `db/migration` se aplican sobre el contenedor limpio (así se ejerce el scenario "migración en base limpia").
- `ddl-auto=none` también en test: se valida que **Hibernate no altera** el esquema.
- Los slices que no tocan BD (`@WebMvcTest`, unit) **no** levantan contenedor → siguen siendo rápidos.

### Convenciones (backend)
- Nombre de método de test orientado a comportamiento: `should_<resultado>_when_<condición>` (identificadores en inglés, propios de Java) y/o `@DisplayName` descriptivo en español referenciando el scenario.
- Estructura AAA con comentarios `// Arrange / // Act / // Assert`.
- Mockea la capa inmediatamente inferior: en tests de controller, mockea services; en tests de service, mockea repositorios. No uses conexiones reales en unit tests.
- Cubre por función: happy path + errores + edge cases + validación.

---

## 6. Cobertura

- La cobertura **no** es la meta; la confianza y la mantenibilidad sí. No infles números con tests triviales.
- Enfoca el esfuerzo en: lógica de negocio crítica (cálculos, permisos, validaciones), interacciones críticas y edge cases propensos a romperse.
- **Frontend**: `pnpm test:coverage` (`@vitest/coverage-v8`); informe HTML en `modules/frontend/coverage/` (ignorado). Sin umbral que rompa el build por ahora.
- **Backend**: JaCoCo vía `mvn verify` cuando se configure; sin umbral que rompa el build por ahora.
- Si en el futuro se exige un mínimo en CI, se configura en `coverage.thresholds` (Vitest) y en la regla de JaCoCo.

---

## 7. Requisitos en Pull Requests

Antes de abrir/fusionar un PR, garantizar:
1. `openspec validate <change> --strict` en verde (specs/change consistentes).
2. **Frontend**: `pnpm validate` (typecheck + lint + test) en verde.
3. **Backend**: `./mvnw -B verify` en verde (Docker disponible para Testcontainers).
4. Nueva funcionalidad incluye pruebas **según las matrices y los scenarios del spec**.
5. Sin *flaky tests*; sin `it.skip`/`@Disabled` en `main` sin justificación documentada.

> `pnpm validate` (front) y `./mvnw verify` (back) son la verificación local completa antes de subir.

---

## 8. Checklist para una nueva capacidad (al aplicar un change)

- [ ] ¿Cada `#### Scenario:` del `spec.md` tiene al menos una prueba que lo referencia?
- [ ] ¿Se eligió el nivel mínimo suficiente por cada scenario (matrices §3/§5)?
- [ ] Front: unit para funciones puras/stores; integración con MSW para hooks de React Query; componentes por roles accesibles; estados loading/error/empty.
- [ ] Back: slice adecuado (`@WebMvcTest`/`@DataJpaTest`/`@SpringBootTest`); persistencia/Flyway sobre Testcontainers; seguridad por rol con `spring-security-test`.
- [ ] ¿Se cubrieron errores y edge cases descritos en los scenarios (no solo el happy path)?
- [ ] ¿Suite local en verde (front y back) antes del commit/PR?
