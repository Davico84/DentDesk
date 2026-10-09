# Tooling y Setup — OdontoRisas

Guía **reproducible** del tooling de calidad. Adaptada del template FixRiver a la estructura real de este repo (monorepo con `modules/frontend`, hooks de git en la **raíz**). Complementa `docs/testing.md` (pruebas) y `docs/coding-style.md`.

> Comandos de frontend desde `modules/frontend/`. Los hooks de commit son de repo (raíz).

---

## 1. Scripts de frontend (`modules/frontend/package.json`)

```jsonc
{
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "lint": "eslint .",
    "typecheck": "tsc -b",
    "test": "vitest run",
    "test:watch": "vitest",
    "test:coverage": "vitest run --coverage",
    "validate": "pnpm typecheck && pnpm lint && pnpm test"
  }
}
```

> **`pnpm validate`** = chequeo completo (typecheck + lint + test). Úsalo antes de subir. El gestor es **pnpm** (`packageManager` fijado en `package.json` para que CI use la misma versión).

---

## 2. Testing — Vitest + RTL + MSW

Instalado en devDependencies: `vitest`, `@vitest/coverage-v8`, `jsdom`, `@testing-library/{react,jest-dom,user-event}`, `msw`.

Archivos (ya presentes):
- `vitest.config.ts` — plugin react, alias `@`, `environment: "jsdom"`, `setupFiles: ["./src/test/setup.ts"]`, `globals: false`, `define` de globals de Vite (`BACKEND_URL`), y **`pool: "threads"`**.
- `src/test/setup.ts` — `@testing-library/jest-dom/vitest` + `afterEach(cleanup)` + ciclo de vida de MSW (`listen/resetHandlers/close`) + shims de jsdom.
- `src/test/mocks/{handlers,server}.ts` — handlers por defecto y `setupServer`.
- `src/test/utils.tsx` — `renderWithProviders` (React Query + Router; se ampliará con sesión/i18n al existir).

> [!NOTE]
> **Windows**: se usa `pool: "threads"` en Vitest. El pool por defecto (`forks`) provocaba `Timeout waiting for worker to respond` al arrancar el worker.

Convención de tests y matriz de decisiones: `docs/testing.md`.

---

## 3. Commits — husky + commitlint (raíz del repo)

A diferencia del template (monorepo con `core.hooksPath` manual), aquí el `.git` y el `package.json` raíz están juntos, así que se usa **husky estándar en la raíz** (aplica a todos los módulos).

Instalado en el `package.json` **raíz**: `husky`, `@commitlint/cli`, `@commitlint/config-conventional`.

- `package.json` (raíz) → `"prepare": "husky"` (inicializa hooks tras `pnpm install`).
- `commitlint.config.mjs` (raíz) → `extends: ['@commitlint/config-conventional']` + tipos de `docs/commits.md`.
- `.husky/commit-msg` → `pnpm exec commitlint --edit "$1"` (rechaza mensajes no-conventional).

Activación: tras clonar, ejecutar **`pnpm install` en la raíz** una vez.

---

## 4. Pre-commit — lint-staged (frontend)

Hook `pre-commit` en la raíz que ejecuta `lint-staged` **acotado a `modules/frontend/`** (los commits de solo-backend no se ven afectados).

- `modules/frontend/lint-staged.config.mjs`:
  ```js
  export default {
    "*.{ts,tsx}": ["eslint --fix"],
    "*.{ts,tsx,json}": () => "tsc -b",
  };
  ```
- `.husky/pre-commit` (raíz): si hay archivos staged bajo `modules/frontend/`, hace `cd modules/frontend && pnpm exec lint-staged`.
- `lint-staged` está en las devDependencies de `modules/frontend`.

---

## 5. CI

Un workflow **por área** en `.github/workflows/`, cada uno con **path filter** (corre solo si cambia su área) y `concurrency` (cancela runs superados). Disparan en push/PR a `main` y `dev`.

| Workflow | Filtro de rutas | Jobs |
|---|---|---|
| `openspec.yml` | `openspec/**` | `validate` → `openspec validate --all --strict` |
| `frontend.yml` | `modules/frontend/**`, `contracts/**` | `lint` (typecheck + eslint) · `test` (`vitest --coverage` + artifact) · **`codegen-drift`** |
| `backend.yml` | `modules/backend/**` | `verify` (`./mvnw -B verify`, incluye **`ContractDriftIT`**) · *(futuro: lint Spotless/Checkstyle, coverage JaCoCo)* |

### 5.1 Los dos guardianes del contrato

El contrato OpenAPI es la fuente única de verdad, así que CI vigila que **la cadena entera** siga sincronizada. Hacen falta **las dos direcciones**: con una sola, el contrato puede mentir sobre el backend sin que nadie se entere (que es exactamente lo que pasaba).

```
backend ──[ContractDriftIT]──► contracts/openapi.json ──[codegen-drift]──► cliente generado
```

| Guardián | Dónde | Qué hace |
|---|---|---|
| **`ContractDriftIT`** | test, dentro del `verify` del backend | Compara la especificación que el backend publica **ahora** con `contracts/openapi.json`. **Cambiar la API sin regenerar el contrato rompe la build.** |
| **`codegen-drift`** | job del workflow de frontend | Regenera el cliente con orval y falla si difiere de lo commiteado. |

> **Por qué el primero es un test y no un job que arranque un servidor**: usa la misma maquinaria de springdoc, corre dentro del `verify` que CI ya ejecuta y rompe la build igual — sin añadir un servidor y un `curl` como piezas frágiles.

**Si CI te dice que hay drift**, regenera y commitea ambos:
```bash
# con el backend arrancado — OJO: con `-o`, nunca canalizando por un script
curl -o contracts/openapi.json http://localhost:8080/v3/api-docs
pnpm --filter odontorisas-frontend generate:api
```
> [!WARNING]
> Exporta el contrato con `curl -o`. Un `curl | python` en Windows lo dejó **doble codificado** (el `stdin` de Python usa cp1252, no UTF-8), lo que reventaba a `ContractDriftIT` y propagaba *mojibake* al cliente generado.

> [!NOTE]
> **Path filters + branch protection**: si más adelante marcas estos checks como *required*, un workflow que no dispara (porque su área no cambió) aparece como *pending/skipped* y puede bloquear el merge. Cuando llegue ese momento, usar "required workflows" a nivel de organización o un job agregador. Por ahora no hay branch protection.

> [!WARNING]
> ### 🚩 Al leer un fallo de CI: mira SIEMPRE el SHA del run
>
> Los workflows llevan `concurrency: cancel-in-progress: true`, así que **cada push cancela el run anterior a media ejecución**. Si haces varios pushes seguidos arreglando algo, quedan runs **cancelados** cuyos logs **siguen visibles** y muestran **fallos ya resueltos**.
>
> Esto nos ha costado **tres rondas** de diagnosticar bugs que ya no existían. Dos síntomas que lo delatan:
>
> - **El error "no desaparece"** por más que lo arregles → estás leyendo el log de un run viejo. **Comprueba el SHA en la cabecera del run** y contrástalo con `git log -1`.
> - **El donut del commit sale rojo aunque los checks estén todos en verde** → el donut agrega **todos** los check runs del commit, y un run **cancelado** no cuenta como verde. Si los checks del último SHA están ✅, está bien.
>
> **Y para encontrar el fallo real:** los tests que fallan por aserción lanzan `AssertionError`, **no tienen `Caused by:`**. Busca `<<< FAILURE!` o `Tests run: … Failures:`. El `Caused by:` solo aparece si el contexto de Spring no arranca.

---

## 6. Testcontainers local (Docker Desktop reciente)

Los `*IT` (Testcontainers) llevan `@Testcontainers(disabledWithoutDocker = true)`: si Testcontainers no valida el entorno Docker, **se saltan limpios** en vez de fallar — así que un `mvn verify` "en verde" localmente puede en realidad no haber probado nada.

**Síntoma**: en Windows, con Docker Desktop reciente (Docker Engine ≥ 29), los `*IT` se saltaban siempre, aunque `docker info`/`docker run` funcionaran perfecto. El log de Maven solo decía *"Could not find a valid Docker environment"*, sin más detalle (hace falta `-Dorg.slf4j.simpleLogger.log.org.testcontainers=debug` para ver la causa real).

**Causa real**: el `pom.xml` fijaba `testcontainers.version=1.21.3` a mano (resto del scaffolding inicial), sobrescribiendo lo que `spring-boot-starter-parent:4.0.6` ya gestiona por su cuenta (`2.0.5` — la versión con la que el propio Spring Boot 4.0 se probó). Esa `1.21.3` habla API Docker `1.32`; Docker Engine 29 exige mínimo `1.44` → el cliente Java de Testcontainers no podía ni validar el entorno.

**Fix** (aplicado): quitar la fijación manual (`testcontainers.version` + el `<dependencyManagement>` que importaba `testcontainers-bom`) y dejar que `spring-boot-starter-parent` decida — hereda `2.0.5` solo. En 2.x, dos `artifactId` cambiaron de nombre (`junit-jupiter`→`testcontainers-junit-jupiter`, `postgresql`→`testcontainers-postgresql`); los paquetes Java (`org.testcontainers.junit.jupiter.Testcontainers`, `org.testcontainers.containers.PostgreSQLContainer`) **no cambiaron** — cero cambios de código.

> [!WARNING]
> **Trampa aparte, ya resuelta pero vale la pena conocerla**: Testcontainers cachea la estrategia de conexión que le funcionó en `~/.testcontainers.properties` (`docker.client.strategy=...`). Si ese archivo quedó con una estrategia rota de un intento anterior, **ignora cualquier `DOCKER_HOST` que fijes después** hasta que lo borres. Si Testcontainers "no ve" un Docker que sabes que funciona, borra ese archivo antes de seguir investigando.

## 7. `JWT_SECRET` local — regenerar uno corto o de ejemplo

Desde `update-jwt-secret-validation` el backend **no arranca** si `app.security.jwt.secret` falta, quedó como el placeholder `${JWT_SECRET}`, es el valor de `secrets.properties.example` o tiene **menos de 32 bytes**. El `Caused by:` del log lo dice (`app.security.jwt.secret debe tener al menos 32 bytes (tiene N)`, `…usa el valor de ejemplo…`), sin mostrar el valor.

Si tu `modules/backend/secrets.properties` es anterior, o lo copiaste del `.example` sin cambiar el secreto, reemplaza **a mano** la línea `JWT_SECRET=...`:

```powershell
openssl rand -base64 48
# sin openssl:
node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"
```

Pega la salida como `JWT_SECRET=<salida>` en tu editor. **No** imprimas `secrets.properties` en la consola, ni pegues el secreto en chats, issues o PRs. Los tests no lo necesitan: usan su propio secreto (`AbstractIntegrationTest.TEST_JWT_SECRET`), que gana a tu archivo, al entorno y a las `-D`.

Cambiar el secreto invalida los access tokens ya emitidos (tendrás que volver a iniciar sesión en local); ver `docs/backend.md` §12.

**¿Ya arrancó el backend?** Cuando está listo para recibir peticiones escribe un bloque destacado en el log (`StartupReadyBanner`), fácil de ver en terminales integradas:

```
============================================================
  >> Dentdesk API lista en http://localhost:8080
    Swagger UI: http://localhost:8080/swagger-ui.html
============================================================
```

Si no aparece, el arranque falló: busca el primer `Caused by:` del log (por ejemplo, un `JWT_SECRET` inválido).