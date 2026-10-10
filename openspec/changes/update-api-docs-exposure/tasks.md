> Solo backend y docs. Sin cambios en `contracts/openapi.json` ni en el frontend.

## 1. Tests primero

- [ ] 1.1 `ApiDocsDefaultTest` (unit): lee `application.yml` del classpath y exige `springdoc.api-docs.enabled` y `springdoc.swagger-ui.enabled` = `${SWAGGER_ENABLED:false}` (scenario *Documentación deshabilitada por defecto*, valor versionado). Debe fallar contra el código actual
- [ ] 1.2 `ApiDocsExposureIT` con `SWAGGER_ENABLED=false`: `/v3/api-docs` y `/swagger-ui.html` → `404`; `POST /auth/login` con cuerpo vacío → `400` (mismo scenario, el resto de la API funciona). Debe fallar contra el código actual
- [ ] 1.3 `OpenApiContractIT`: casos de `/v3/api-docs` → `200` y `/swagger-ui.html` sirve la UI (scenarios *Exposición en runtime* y *Swagger UI disponible al habilitarla*)

## 2. Implementación

- [ ] 2.1 `application.yml`: bloque `springdoc` con `${SWAGGER_ENABLED:false}` en las dos propiedades (design D1)
- [ ] 2.2 `@TestPropertySource(properties = "SWAGGER_ENABLED=true")` en `ContractDriftIT` y `OpenApiContractIT` (design D2); mensaje `REGENERATE` de `ContractDriftIT` menciona el interruptor
- [ ] 2.3 `StartupReadyBanner`: default `false` y texto `Swagger UI: desactivado (SWAGGER_ENABLED=false)`; ajustar `StartupReadyBannerTest` (design D5)
- [ ] 2.4 `secrets.properties.example`: `SWAGGER_ENABLED=true` con el comentario "false o ausente en despliegue"
- [ ] 2.5 Mutación: `ContractDriftIT` con `SWAGGER_ENABLED=false` falla con `404`; restaurar (design D4)

## 3. Docs y cierre

- [ ] 3.1 `docs/backend.md` (§ API docs y §12), `docs/architecture.md`, `docs/tooling-setup.md` (aviso de arranque y cómo encender Swagger en local), `readme.md` y el `context` de `openspec/config.yaml`: quitar "por perfil", documentar `SWAGGER_ENABLED`
- [ ] 3.2 `./mvnw -B verify` verde con `Skipped: 0`; `contracts/openapi.json` sin cambios
- [ ] 3.3 `openspec validate update-api-docs-exposure --strict` y medir a mano el requirement MODIFIED (≤ 500 caracteres)
