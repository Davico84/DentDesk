# Design — update-api-docs-exposure

## Context

- `application.yml` no tiene bloque `springdoc`: valen los defaults de la librería, que **encienden** `/v3/api-docs` y Swagger UI.
- `PublicPaths` lista `/v3/api-docs/**`, `/swagger-ui/**` y `/swagger-ui.html` como públicas: sin sesión.
- Configuración única, sin perfiles (`docs/backend.md` §12): todo valor configurable es un placeholder que se resuelve desde `./secrets.properties` en local o desde variables de entorno en despliegue.
- Tests que leen `/v3/api-docs`: `ContractDriftIT` (contrato versionado vs. backend) y `OpenApiContractIT` (status reales y errores documentados). Ningún test del frontend ni de CI depende del backend en ejecución para el contrato.
- `StartupReadyBanner` lee `springdoc.swagger-ui.enabled` con default `true`.
- Antecedente: el mismo fix en HSO (`update-api-docs-exposure`). Se reutiliza su diseño, sin la parte de tooling de plantilla (`scripts/project`), que este repo no tiene.

## Goals / Non-Goals

**Goals:** documentación apagada por defecto en cualquier entorno que no la pida; un solo interruptor; los tests de contrato siguen funcionando y prueban ambos estados.

**Non-Goals:** los del proposal (proteger Swagger con rol, perfiles, tocar `PublicPaths`, export sin levantar la app, Actuator).

## Decisions

### D1. Un interruptor, dos propiedades

```yaml
springdoc:
  api-docs:
    enabled: ${SWAGGER_ENABLED:false}
  swagger-ui:
    enabled: ${SWAGGER_ENABLED:false}
```

Un solo nombre para ambas: no tiene sentido servir la UI sin el documento, ni el documento "sin UI" como media medida. Se llama `SWAGGER_ENABLED` porque es lo que el equipo busca. Va en `secrets.properties.example` **comentado** (`# SWAGGER_ENABLED=true`): copiar el archivo tal cual a un despliegue deja la documentación apagada; en local se descomenta. *(Revisión externa: con la línea activa, el README manda copiar el `.example` y un despliegue podía encenderla sin querer.)*

*Alternativa descartada:* perfiles (`application-prod.yml`): contradice la configuración única.

### D2. Tests de contrato: activación explícita por clase

`@TestPropertySource(properties = "SWAGGER_ENABLED=true")` en `ContractDriftIT` y `OpenApiContractIT`. Los placeholders resuelven propiedades de cualquier fuente, no solo variables de entorno. No se activa en `AbstractIntegrationTest`: si se hiciera ahí, ningún IT podría probar el apagado (`@DynamicPropertySource` tiene precedencia sobre `@TestPropertySource`). Las dos clases comparten la misma propiedad, así que Spring reutiliza el contexto cacheado entre ellas.

### D3. `PublicPaths` no cambia

Con la documentación apagada, springdoc no registra sus endpoints y las rutas públicas responden `404` desde MVC, con el formato uniforme de error. Hacerlas privadas según el interruptor solo cambiaría `404` por `401` y rompería la regla de una sola lista estática.

### D4. Tests herméticos

Los tests importan también `./secrets.properties` (failsafe corre en `modules/backend`) y leen el entorno: si un desarrollador tiene `SWAGGER_ENABLED=true` en su archivo, un test "sin la propiedad" no probaría el default. Por eso **ningún test depende de la ausencia** de la propiedad:

- **Apagado** — `ApiDocsExposureIT` con `@TestPropertySource(properties = "SWAGGER_ENABLED=false")`, que gana a `secrets.properties` y al entorno: `GET /v3/api-docs` y `/swagger-ui.html` → `404`, igual que las rutas secundarias (`/v3/api-docs/swagger-config`, `/v3/api-docs/{grupo}`, `/swagger-ui/index.html` y sus assets); `/v3/api-docs.yaml` → `401` (no está en `PublicPaths`); una operación normal (`POST /auth/login` con cuerpo vacío → `400`) sigue respondiendo.
- **Valor por defecto** — `ApiDocsDefaultTest` (unit, sin Spring): lee `application.yml` del classpath y exige que las dos propiedades valgan exactamente `${SWAGGER_ENABLED:false}`. Determinista en cualquier máquina.
- **Encendido** — en `OpenApiContractIT`: `/v3/api-docs` → `200` y `/swagger-ui.html` sirve la UI (`200`, o redirección a `/swagger-ui/index.html` que responde `200`).
- **Mutación** — cambiar temporalmente la propiedad de `ContractDriftIT` a `false` (no quitarla: quitarla depende del entorno) → tiene que fallar con `404`; restaurar.

### D5. Aviso de arranque

`StartupReadyBanner` anuncia Swagger solo si **ambas** propiedades (`springdoc.swagger-ui.enabled` y `springdoc.api-docs.enabled`) están encendidas, cada una con default `false`. Con un override directo de springdoc que apague solo el documento, la UI abriría vacía: el aviso la da por desactivada. *(Revisión externa.)* El texto del estado apagado nombra el interruptor que hay que tocar: `Swagger UI: desactivado (SWAGGER_ENABLED=false)`.

## Risks / Trade-offs

- **Desarrolladores con `secrets.properties` ya creado pierden Swagger en local** hasta agregar `SWAGGER_ENABLED=true`. Mitigación: el aviso de arranque lo dice, y `tooling-setup.md` y `readme.md` lo explican.
- **El export del contrato con `curl`** exige el backend con el interruptor encendido. Mitigación: el mensaje de `ContractDriftIT` (`REGENERATE`) lo menciona.

## Migration Plan

Sin migración de datos. En cada despliegue: no definir `SWAGGER_ENABLED` (o definirlo `false`). En local: agregar `SWAGGER_ENABLED=true` a `secrets.properties`.
