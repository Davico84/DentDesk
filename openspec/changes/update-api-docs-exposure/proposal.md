## Why

Swagger UI y `/v3/api-docs` están **siempre públicos**: `application.yml` no configura springdoc (sus valores por defecto los encienden) y las rutas están en `PublicPaths`. Un despliegue expondría a cualquiera, sin sesión, todas las operaciones de la API con sus DTOs, validaciones y errores, incluidas las clínicas (historia, odontograma, notas de consulta). `docs/backend.md` y `docs/architecture.md` dicen "Swagger habilitado por perfil", pero no hay perfiles (configuración única) y nunca se implementó. Hay que cerrarlo **antes** del primer despliegue. Mismo fix que ya se aplicó en el proyecto hermano HSO (`update-api-docs-exposure`, 2026-09-30).

## What Changes

- Un solo interruptor por entorno, **`SWAGGER_ENABLED`**, gobierna `springdoc.api-docs.enabled` y `springdoc.swagger-ui.enabled`. **Por defecto `false`**: un despliegue que no lo configure no expone la documentación.
- En local se enciende desde `secrets.properties` (`secrets.properties.example` lo trae en `true`).
- Los tests de contrato (`ContractDriftIT`, `OpenApiContractIT`) lo encienden de forma explícita; el resto de la suite corre con el valor por defecto.
- Con el interruptor apagado, `/v3/api-docs` y `/swagger-ui.html` responden `404` (no existen), aunque sigan en `PublicPaths`.
- El aviso de arranque (`StartupReadyBanner`) informa el estado con el nombre del interruptor y asume apagado si no está definido.
- Docs: `backend.md`, `architecture.md`, `tooling-setup.md`, `readme.md` y el `context` de `openspec/config.yaml` dejan de decir "por perfil".
- Sin cambios en `contracts/openapi.json` ni en el frontend: el codegen lee el archivo versionado, no el backend.

## Capabilities

### New Capabilities

_(ninguna)_

### Modified Capabilities

- `api-type-contracts`: el requirement *Especificación OpenAPI publicada y versionada* pasa a exponer la especificación en runtime solo con la documentación habilitada, y suma los scenarios de "deshabilitada por defecto" y "Swagger UI al habilitarla".

## Non-goals

- Proteger Swagger con autenticación o rol en producción (opción futura si se quiere exponer a un equipo).
- Perfiles de Spring (`application-*.yml`): la configuración sigue siendo única con placeholders.
- Tocar `PublicPaths` según el interruptor: con la documentación apagada las rutas no existen y responden `404`; hacerlas privadas solo cambiaría `404` por `401`.
- Exportar el contrato sin levantar la aplicación (HSO lo hace con `-Dcontract.update=true`): mejora útil, pero otro change. El export sigue siendo arrancar el backend con `SWAGGER_ENABLED=true` y hacer `curl`.
- Actuator (`show-details: always`): mismo tipo de exposición, va con el change de despliegue.

## Impact

- **Backend**: `application.yml` (bloque `springdoc`), `StartupReadyBanner`, `secrets.properties.example`. Tests: `@TestPropertySource` en `ContractDriftIT` y `OpenApiContractIT`; test nuevo del comportamiento apagado y test del valor por defecto.
- **Entornos**: quien ya tiene `secrets.properties` debe agregar `SWAGGER_ENABLED=true` para seguir viendo Swagger en local (aviso en la doc).
- **Docs**: `docs/backend.md`, `docs/architecture.md`, `docs/tooling-setup.md`, `readme.md`, `openspec/config.yaml`.
