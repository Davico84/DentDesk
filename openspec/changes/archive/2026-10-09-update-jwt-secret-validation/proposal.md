## Why

Si `JWT_SECRET` no está definida (ni en el entorno ni en `secrets.properties`), Spring Boot **no falla**: deja el placeholder sin resolver y `app.security.jwt.secret` vale el texto literal `${JWT_SECRET}` (13 caracteres). `TokenService` lo usa como clave HMAC-256 sin validar nada. Un despliegue que olvide la variable arrancaría firmando con un secreto público: **cualquiera que lea `application.yml` podría fabricar un JWT de `ADMIN` de cualquier organización** (el `oid` va dentro del token firmado). Lo mismo con el valor de ejemplo de `secrets.properties.example`, que es justo lo que deja el `cp secrets.properties.example secrets.properties` del `readme.md`.

Verificado y corregido en el proyecto hermano HSO (change archivado `2026-09-30-update-jwt-secret-validation`); el código afectado es idéntico aquí.

## What Changes

- La aplicación **no arranca** si el secreto de firma de los tokens no es seguro: ausente o en blanco, con un placeholder sin resolver (`${…}`), igual al valor de ejemplo de `secrets.properties.example`, o de **menos de 32 bytes** (UTF-8), el mínimo para HMAC-256. El error nombra `app.security.jwt.secret` y **nunca incluye el valor**, tampoco en el log de arranque (la validación vive en `TokenService`, no en el binding: ver design D1).
- Los tests de contexto completo usan un **secreto de test propio** (≥ 32 bytes) registrado sobre la propiedad final `app.security.jwt.secret` en `AbstractIntegrationTest`. Hoy se registra el placeholder `JWT_SECRET`, que una propiedad de sistema `-Dapp.security.jwt.secret` o la variable `APP_SECURITY_JWT_SECRET` pueden sobrescribir: los tests dependerían de la configuración de quien los ejecuta.
- `secrets.properties.example` y `readme.md` indican el requisito (≥ 32 **bytes** aleatorios, `openssl rand -base64 48`) y que el valor de ejemplo ya no arranca.
- **Agregado durante el apply, a pedido del user:** un **aviso de "backend listo"** en el log al terminar de arrancar, con el nombre del producto, la URL con el puerto real y la URL de Swagger UI. Es la señal positiva de que el arranque, incluido el secreto, pasó; hasta ahora solo había señal de error. Portado de `StartupReadyBanner` de HSO. Lee solo propiedades no sensibles, así que no puede filtrar secretos por construcción (design D5).

## Non-goals

- Rotación de claves, varias claves activas o algoritmos asimétricos (RS256).
- Validar el resto de propiedades de seguridad (`issuer`, TTLs, CORS): se valorará aparte.
- Medir entropía real del secreto: se exigen longitud y que no sea un valor conocido.
- Cambios de API, contrato OpenAPI o frontend.

## Capabilities

### New Capabilities
<!-- ninguna -->

### Modified Capabilities
- `authentication`: ADDED "Secreto de firma de tokens obligatorio y robusto".
- `project-foundation`: ADDED "Aviso de backend listo al arrancar".

## Impact

- **Backend**: nuevo `infra.security.JwtSecretRules`, llamado desde el constructor de `TokenService`; `AbstractIntegrationTest` registra `app.security.jwt.secret` con una constante pública `TEST_JWT_SECRET`.
- **Tests**: `JwtSecretRulesTest` (unit), `JwtSecretStartupTest` (`ApplicationContextRunner`, sin Docker), `JwtSecretStartupOutputTest` (salida real del arranque, sin Docker), `JwtSecretHermeticityIT` (Testcontainers). Los `@SpringBootTest` existentes ya heredan de `AbstractIntegrationTest`; los tres `@WebMvcTest` excluyen `JwtAuthenticationFilter` y no crean `TokenService`.
- **Config/Docs**: `secrets.properties.example`, `readme.md` (arranque local), `docs/backend.md` §11 (reglas; y corrige la mención obsoleta a `application-dev-secrets.yml`) y §12 (rotación, réplicas), `docs/tooling-setup.md` (regenerar un secreto local corto o de ejemplo).
- **Despliegue**: una instancia sin `JWT_SECRET` válido deja de arrancar (intencionado: hoy arranca insegura). Un `secrets.properties` local con el valor de ejemplo o corto también deja de arrancar: hay que regenerarlo a mano (design D4).
