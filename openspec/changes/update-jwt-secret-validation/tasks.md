> Solo backend + config + docs. Sin cambios de API, contrato ni frontend. Portado de HSO (`2026-09-30-update-jwt-secret-validation`).
> Nunca abrir, leer ni imprimir `modules/backend/secrets.properties` ni un secreto real, tampoco en tests: comparar por hash. `Skipped: 0` exige Docker Desktop encendido.

## 1. Validación (D1)

- [ ] 1.1 `infra.security.JwtSecretRules.validate`: reglas y orden de D1 (null/blanco, `${`, ejemplo comparado con `strip()`, < 32 bytes UTF-8; el secreto no se recorta), `IllegalStateException` que nombra `app.security.jwt.secret` y nunca el valor
- [ ] 1.2 `TokenService` llama a `JwtSecretRules.validate(props.jwt() == null ? null : props.jwt().secret())` en su constructor antes de `Algorithm.HMAC256`
- [ ] 1.3 `JwtSecretRulesTest` (unit): un caso por regla (mensaje con la propiedad y sin el valor), incluidos `${OTRA_VARIABLE}` y el ejemplo con espacios alrededor; exactamente 32 bytes ASCII, multibyte de 16 caracteres / 32 bytes y aleatorio de 48 bytes válidos; sincronía con `secrets.properties.example` (igual a la constante, ≥ 32 bytes, rechazado por la regla de ejemplo)
- [ ] 1.4 `JwtSecretStartupTest` (`ApplicationContextRunner` + `TokenService`): sin ninguna `app.security.jwt.*` (mensaje "obligatorio", no `NullPointerException`), `secret` sin definir, placeholder literal, corto → no arranca con el mensaje de su regla y la cadena de errores sin el valor; válido → arranca y firma/verifica un token con `oid`
- [ ] 1.5 `JwtSecretStartupOutputTest` (`OutputCaptureExtension` + `SpringApplication` mínima, `spring.config.location` y `spring.config.additional-location` neutralizados por argumento): la salida real del arranque fallido nombra la propiedad y no contiene el valor

## 2. Tests herméticos (D2, D3)

- [ ] 2.1 `AbstractIntegrationTest`: sustituir `registry.add("JWT_SECRET", …)` por `registry.add("app.security.jwt.secret", () -> TEST_JWT_SECRET)` con constante pública ≥ 32 bytes; actualizar el comentario de la clase
- [ ] 2.2 Revisar que ningún `@SpringBootTest` quede fuera de `AbstractIntegrationTest`, que ningún `@WebMvcTest`/`@Import` cree `TokenService` y que ningún test fije `app.security.jwt.secret` con `@TestPropertySource` o `properties =` (hoy: ninguno, confirmado por Codex; volver a confirmar tras los cambios)
- [ ] 2.3 `JwtSecretHermeticityIT`: secreto efectivo == `TEST_JWT_SECRET` (por hash) en `SecurityProperties` y `Environment`
- [ ] 2.4 `JwtSecretHermeticityIT` ejecutado vía Maven con `-DJWT_SECRET=…` y `-Dapp.security.jwt.secret=…` contaminantes → sigue siendo el de test
- [ ] 2.5 Mutación: sin el registro de 2.1, el IT con las `-D` contaminantes falla; restaurar
- [ ] 2.6 Mutación: si el mensaje de `JwtSecretRules` incluyera el valor, fallan los tests de filtración (1.3-1.5); restaurar
- [ ] 2.7 `./mvnw -B verify` verde con `Skipped: 0` (Docker Desktop encendido)

## 3. Docs y cierre

- [ ] 3.1 `secrets.properties.example`: requisito de 32 bytes aleatorios y cómo generarlo (valor de ejemplo sin cambiar)
- [ ] 3.2 `readme.md`: tras copiar el `.example`, generar un `JWT_SECRET` propio
- [ ] 3.3 `docs/backend.md` §11 (reglas, validación en `TokenService` y por qué; nunca loguear `SecurityProperties`/`Jwt`; nunca `@TestPropertySource` para el secreto; corrige `application-dev-secrets.yml` en la línea 385) y §12 (obligatorio, rotación: invalida access pero no sesiones; revocar `refresh_tokens` si se filtró; réplicas con el mismo secreto)
- [ ] 3.4 `docs/tooling-setup.md`: regenerar a mano un secreto local corto o de ejemplo
- [ ] 3.5 Prueba manual (PowerShell): `./mvnw spring-boot:run "-Dspring-boot.run.arguments=--app.security.jwt.secret=corto"` no arranca, el `Caused by:` dice "… al menos 32 bytes (tiene 5)", y el log de la aplicación (desde el banner de Spring, excluida la línea de comando que Maven repite) no contiene `corto`
- [ ] 3.6 `openspec validate update-jwt-secret-validation --strict`
