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

Un workflow **por área** en `.github/workflows/`, con `concurrency` (cancela runs superados). Disparan en push y PR a `main` y `dev`:

- **En los PR corren siempre los tres**, toquen lo que toquen: sin filtro de rutas, porque `main` exige los seis checks y uno que no se dispara deja el merge pendiente para siempre (`update-ci-required-checks`).
- **En el push** (después del merge) cada uno corre solo si cambió su área: el PR ya corrió todo sobre el resultado del merge.

| Workflow | Filtro de rutas (solo push) | Jobs → **check** que publican |
|---|---|---|
| `openspec.yml` | `openspec/**` | `validate` → **`openspec-validate`**: nombres únicos de los checks (abajo) + `openspec validate --all --strict` |
| `frontend.yml` | `modules/frontend/**`, `contracts/**`, `pnpm-*` | `lint` → **`frontend-lint`** (typecheck + eslint) · `codegen-drift` → **`frontend-codegen-drift`** · `test` → **`frontend-test`** (`vitest --coverage` + artifact) · `e2e-smoke` → **`frontend-e2e-smoke`** |
| `backend.yml` | `modules/backend/**` | `verify` → **`backend-verify`** (`./mvnw -B verify`, incluye **`ContractDriftIT`**) · *(futuro: lint Spotless/Checkstyle, coverage JaCoCo)* |

**El nombre del check es el `name:` del job**, no el `Backend / verify` que muestra la interfaz: es lo que compara el Ruleset. Convención: prefijo del área (`backend-`, `frontend-`, `openspec-`). El Ruleset no distingue de qué workflow viene un check, así que **cada nombre exigido lo publica un solo job, y ese job corre en todo PR hacia `main` y `dev`** (`pull_request` sin filtro de rutas ni ramas excluidas y, si declara `branches`/`types`, con `main` y `dev` y con `opened`/`synchronize`/`reopened`; job con `runs-on` y sin `if:`). Un nombre de job dinámico solo se admite como `${{ matrix.<clave> }}` con valores literales, que se expanden; cualquier otro `${{` falla: lo comprueba `.github/scripts/check-ci-check-names.sh` en cada PR (paso de `openspec-validate`, con `yq` de versión y SHA-256 fijos), leyendo los nombres exigidos de `.github/rulesets/main.json`. En local: `YQ=<ruta de yq> bash .github/scripts/check-ci-check-names.sh`.

### 5.0 Protección de `main` (Ruleset)

`main` se protege con un Ruleset **versionado** en `.github/rulesets/main.json`: PR obligatorio (0 aprobaciones, solo commit de merge), los seis checks en verde publicados por GitHub Actions (`integration_id` 15368), **sin** exigir la rama al día (cada release deja en `main` un merge que `dev` no tiene) y sin force push ni borrado. `dev` no se protege.

La protección se cambia **editando el archivo en un PR** y volviéndolo a aplicar, nunca solo en la interfaz:

```bash
# crear (la primera vez)
gh api --method POST repos/{owner}/{repo}/rulesets --input .github/rulesets/main.json
# actualizar: el id sale de `gh api repos/{owner}/{repo}/rulesets`
gh api --method PUT repos/{owner}/{repo}/rulesets/<id> --input .github/rulesets/main.json
# comparar lo aplicado con el archivo (falla ante cualquier diferencia; necesita permiso de admin)
python .github/scripts/ruleset-check.py
```

`ruleset-check.py` falla cerrado: sin Ruleset `main` o con más de uno, sin `bypass_actors` en la respuesta o con cualquier diferencia (el orden no cuenta). Si GitHub devuelve parámetros por defecto que el archivo no tiene, se agregan al archivo para que diga exactamente lo aplicado. Se corre al aplicar y en la revisión mensual (`docs/deployment.md`).

Cosas que dejan un check exigido sin publicar (y el merge pendiente):
- **`[skip ci]`** en un commit del PR: no usarlo en ramas de PR.
- **Un YAML inválido**: el workflow no corre.
- **Merge Queue**: no se usa. Si se activa, los tres workflows deben sumar el evento `merge_group`, o la cola espera checks que nunca llegan.

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
curl -o contracts/openapi.json http://localhost:8080/api/v3/api-docs
pnpm --filter dentdesk-frontend generate:api
```
> [!WARNING]
> Exporta el contrato con `curl -o`. Un `curl | python` en Windows lo dejó **doble codificado** (el `stdin` de Python usa cp1252, no UTF-8), lo que reventaba a `ContractDriftIT` y propagaba *mojibake* al cliente generado.


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
    Swagger UI: http://localhost:8080/api/swagger-ui.html
============================================================
```

Con `SWAGGER_ENABLED` ausente o en `false` la segunda línea dice `Swagger UI: desactivado (SWAGGER_ENABLED=false)`: la documentación está apagada por defecto para que un despliegue no exponga la API. En local, agrega `SWAGGER_ENABLED=true` a tu `secrets.properties` (el `.example` trae la línea **comentada**: copiarlo tal cual a un despliegue no enciende nada). Hace falta también para exportar el contrato con `curl` (`ContractDriftIT` te lo recuerda).

Si no aparece, el arranque falló: busca el primer `Caused by:` del log (por ejemplo, un `JWT_SECRET` inválido).

## 8. Correo local con Mailpit — primer acceso sin cuenta SMTP

No hay registro público: toda cuenta nace de una **invitación por correo** (`docs/deployment.md` §11). En local, en vez de una cuenta SMTP real, se usa **Mailpit**, un servidor SMTP de prueba con bandeja web que ya viene en `modules/backend/compose.yaml` (`update-project-foundation-local-mail`).

**Desde un clon limpio**, en `modules/backend`:

1. `cp secrets.properties.example secrets.properties` y completa `DB_*` y `JWT_SECRET` (§7).
2. Descomenta el bloque **"Correo LOCAL con Mailpit"** del final: `MAIL_ENABLED=true`, `MAIL_LOCAL_SMTP=true`, `MAIL_HOST=localhost`, `MAIL_PORT=1025`, `MAIL_FROM`, `MAILPIT_SMTP_PORT`, `MAILPIT_WEB_PORT`.
3. Agrega las variables del administrador de plataforma (`PLATFORM_ADMIN_*`, `docs/deployment.md` §11.1) para el primer arranque.
4. `docker compose --env-file secrets.properties up -d` levanta PostgreSQL y Mailpit.
5. `./mvnw spring-boot:run` y, en otra terminal, desde la raíz: `pnpm --filter dentdesk-frontend dev`.
6. Da de alta una clínica con `.\scripts\new-clinic.ps1` (`docs/deployment.md` §11.2). La invitación aparece en **http://localhost:8025**: su enlace abre el registro en `http://localhost:5173` y trae el código de verificación.
7. Los correos de la cuenta (recuperación de contraseña, aviso de cambio) también llegan a esa bandeja.

**Puertos.** `MAIL_PORT` es el puerto al que se conecta el backend; `MAILPIT_SMTP_PORT`, el que publica Mailpit en tu máquina. Tienen que coincidir (1025 por defecto). Si 1025 u 8025 están ocupados, cambia `MAILPIT_SMTP_PORT` junto con `MAIL_PORT`, o `MAILPIT_WEB_PORT`. Mailpit solo escucha en `127.0.0.1`: la bandeja no queda expuesta a tu red.

**Por qué el backend no arranca con `MAIL_LOCAL_SMTP=true` y otro host.** El modo local manda el correo **sin STARTTLS ni autenticación**. Para que no llegue a un despliegue por error, con el correo habilitado solo acepta `MAIL_HOST` igual a `localhost`, `127.0.0.1` o `::1` (sin distinguir mayúsculas, ignorando espacios); con cualquier otro valor el arranque falla y el `Caused by:` nombra `MAIL_LOCAL_SMTP` y el host. Para usar un proveedor real (Brevo), comenta el bloque local y vuelve a los `MAIL_*` del proveedor.

> ⚠️ La salvaguarda compara el **texto** del host: protege contra un error de configuración, no contra quien controla la máquina. Un túnel SSH o un relay en `localhost` hacia un SMTP real, con el modo local encendido, enviaría correo real **sin cifrar**. No lo uses así.
