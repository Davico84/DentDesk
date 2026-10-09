## Context

- `application.yml:49`: `app.security.jwt.secret: ${JWT_SECRET}` sin default. Si `JWT_SECRET` falta, el binder de Boot enlaza el literal `${JWT_SECRET}` (13 caracteres). Verificado en HSO con el mismo `application.yml` y la misma versión de Boot.
- `SecurityProperties` es un record sin validación; `TokenService.java:33` hace `Algorithm.HMAC256(props.jwt().secret())` (java-jwt no exige longitud mínima).
- `AbstractIntegrationTest` registra con `@DynamicPropertySource` los **placeholders** que referencia `application.yml` (`DB_URL`, `DB_USERNAME`, …, `JWT_SECRET`), no las propiedades `spring.*`: así lo exigió Flyway (ver el comentario de la clase). Para el datasource eso se queda igual.
- Todos los `@SpringBootTest` heredan (directa o indirectamente, vía `AbstractRlsIntegrationTest`/`AbstractTenantAwareIntegrationTest`) de `AbstractIntegrationTest`. Los `@WebMvcTest` (`BrandingControllerTest`, `PatientControllerTest`, `Cie10ControllerTest`) excluyen `JwtAuthenticationFilter` y no crean `TokenService`; `AuthServiceTest` lo mockea; `TokenServiceTest` lo construye a mano con un secreto de 51 bytes.
- CI (`backend.yml`, `working-directory: modules/backend`) no define `JWT_SECRET` ni tiene `secrets.properties`. No hay `project:apply` en este repo: el arranque local es `cp secrets.properties.example secrets.properties` (`readme.md`), que deja el valor de ejemplo.
- Implementación de referencia: HSO, change archivado `2026-09-30-update-jwt-secret-validation` (revisado por Codex en dos pasadas). Este design la adapta; no la reinventa.

## Goals / Non-Goals

**Goals:** imposible arrancar con un secreto inseguro; mensajes útiles que **nunca** revelan el valor (tampoco el log de arranque); tests con un secreto de precedencia determinista.

**Non-Goals:** ver `proposal.md`.

## Decisions

### D1. Validación en `TokenService`, no en el binding
`JwtSecretRules.validate(String secret)` (`infra.security`, clase final con constructor privado), llamada **en el constructor de `TokenService`** antes de `Algorithm.HMAC256`, con el secreto extraído de forma segura ante nulos: `props.jwt() == null ? null : props.jwt().secret()` (sin ninguna propiedad `app.security.jwt.*`, el binder deja `jwt()` en `null`; sin esta guarda saltaría un `NullPointerException` sin el mensaje contractual). Lanza `IllegalStateException` con mensajes propios, **sin el valor**, en este orden:
1. `null`, vacío o blanco → "app.security.jwt.secret es obligatorio: define JWT_SECRET";
2. contiene `${` (cualquier placeholder, no solo `${JWT_SECRET}`) → "app.security.jwt.secret no está resuelto: define la variable JWT_SECRET";
3. `secret.strip()` igual al valor de ejemplo → "app.security.jwt.secret usa el valor de ejemplo de secrets.properties.example: genera uno propio (openssl rand -base64 48)";
4. `< 32` bytes UTF-8 → "app.security.jwt.secret debe tener al menos 32 bytes (tiene N); genera uno con openssl rand -base64 48".

32 bytes = tamaño de la salida de SHA-256, mínimo de clave para HS256 (RFC 7518 §3.2). Se cuentan **bytes**, no caracteres: es lo que recibe el HMAC. Exactamente 32 bytes es válido.

**Espacios periféricos** (revisión Codex): el secreto **no se recorta** — el HMAC firma con el valor tal cual, y recortarlo cambiaría la clave efectiva respecto a la configurada. Los espacios cuentan como bytes. Solo la comparación con el valor de ejemplo usa `strip()`: `java.util.Properties` quita los espacios iniciales del valor pero **conserva los finales**, así que `JWT_SECRET=<ejemplo> ` (espacio final invisible) pasaría una comparación exacta y arrancaría con un secreto público.

**Por qué no en el constructor del record** `SecurityProperties.Jwt`: un fallo durante el *binding* lo describe el `BindFailureAnalyzer` de Boot con "Property: … Value: … Reason: …", que **imprimiría el secreto** (p. ej. uno real pero corto) en el log de arranque. Un fallo al crear `TokenService` se describe con el mensaje de la excepción, que controlamos. `TokenService` se crea siempre en la aplicación (lo usan `AuthService` y `JwtAuthenticationFilter`), así que el arranque falla igual.
**Alcance de la garantía "no revela el valor"**: vale para el **flujo actual** (verificado en revisión: no hay binding failure, ni log ni `toString` de `SecurityProperties` con el secreto). No es una garantía contra código futuro: `SecurityProperties.Jwt` es un record y su `toString()` **incluye el secreto**, así que loguear `SecurityProperties`, el record `Jwt` o envolver la excepción con contexto propio lo filtraría. Se documenta como regla en `docs/backend.md` §11.
*Descartado*: `@Validated` + Bean Validation en `SecurityProperties` (mismo problema: el informe de binding incluye el valor rechazado).

**Valor de ejemplo sincronizado**: la constante `EXAMPLE_SECRET` duplica el `JWT_SECRET` del `.example` (`cambia-este-secreto-largo-y-aleatorio`, 37 bytes). `JwtSecretRulesTest` lee `secrets.properties.example` del repo y exige que su `JWT_SECRET` sea igual a la constante, tenga ≥ 32 bytes (si no, la regla de longitud taparía la de ejemplo y el test no detectaría una desincronización) y sea rechazado **por la regla de ejemplo**.

### D2. Secreto de test con precedencia determinista
En `AbstractIntegrationTest`: `registry.add("app.security.jwt.secret", () -> TEST_JWT_SECRET)`, con `public static final String TEST_JWT_SECRET` (≥ 32 bytes, claramente de test). Sustituye al actual `registry.add("JWT_SECRET", …)`.
**Por qué la propiedad final y no el placeholder**: registrar `JWT_SECRET` solo fija el valor *del placeholder*; una fuente que defina directamente `app.security.jwt.secret` (propiedad de sistema `-Dapp.security.jwt.secret`, variable `APP_SECURITY_JWT_SECRET` por relaxed binding) gana a `application.yml` y el placeholder ni se evalúa. Registrando la propiedad final, gana al `secrets.properties` importado, a `application.yml`, a las variables de entorno y a las propiedades de sistema / `-D` — las fuentes que existen en local y en CI.
**Excepciones** ([orden oficial de PropertySources](https://docs.spring.io/spring-boot/4.0/reference/features/external-config.html#features.external-config.files.property-sources), revisión Codex): `@DynamicPropertySource` **no** gana a `@TestPropertySource` ni a la configuración global de DevTools (`~/.config/spring-boot`). Ninguna existe hoy en el repo (DevTools no es dependencia); regla: **no** definir `app.security.jwt.secret` con `@TestPropertySource` ni `properties =` de `@SpringBootTest` — un test que necesite otro secreto usa su propio contexto, no la suite común.
El resto de registros (`DB_*`) no cambian: su forma responde a Flyway, no a esta decisión.
Las slices `@WebMvcTest` no crean `TokenService`: no necesitan secreto. Si una slice futura lo necesita, usa la misma constante.
*Descartado*: `src/test/resources/application.properties` (no gana de forma garantizada al archivo importado ni al entorno); `systemPropertyVariables` en surefire/failsafe (una `-D` o variable externa podría competir).

### D3. Tests
- **`JwtSecretRulesTest`** (unit, sin Spring): un caso por regla — `null`, `""`, `"   "`, `"\t"`, `"${JWT_SECRET}"`, `"${OTRA_VARIABLE}"`, valor de ejemplo, valor de ejemplo con espacios alrededor, 31 bytes ASCII (inválidos: mensaje con `app.security.jwt.secret`, el fragmento de su regla y **sin el valor probado**); exactamente 32 bytes ASCII, multibyte de 16 caracteres y 32 bytes (`"ñ".repeat(16)`), y un aleatorio de 48 bytes en base64 (válidos); sincronía con `secrets.properties.example` (D1).
- **`JwtSecretStartupTest`** (sin Docker, `ApplicationContextRunner` con `ConfigurationPropertiesAutoConfiguration`, `@EnableConfigurationProperties(SecurityProperties.class)` y `TokenService`): **ninguna** propiedad `app.security.jwt.*` (→ `jwt()` nulo: mensaje "obligatorio", no `NullPointerException`); `secret` sin definir con el resto presentes; placeholder literal; corto con marcador único → `hasFailed()` con el mensaje de su regla y la **cadena completa** de `getStartupFailure()` sin el valor; válido → arranca y un token emitido (`issueAccessToken(userId, email, organizationId, role)`) se verifica con su `oid`.
- **`JwtSecretStartupOutputTest`** (sin Docker, `OutputCaptureExtension`): `SpringApplication` real y mínima (`WebApplicationType.NONE`; `spring.config.location` **y** `spring.config.additional-location` como argumentos de línea de comandos apuntando a un recurso inexistente opcional, para no leer `application.yml`, `secrets.properties` ni una `SPRING_CONFIG_ADDITIONAL_LOCATION` del entorno — los argumentos ganan a la variable) con un secreto corto distintivo; espera el fallo y comprueba que la salida capturada (log de arranque + informe de los `FailureAnalyzer`) nombra `app.security.jwt.secret` y **no** contiene el valor.
- **`JwtSecretHermeticityIT`** (contexto completo): el secreto efectivo (`SecurityProperties` y `Environment`) es `TEST_JWT_SECRET`, comparando **por hash SHA-256**, nunca imprimiendo. Se ejecuta además con propiedades de sistema contaminantes: `./mvnw -B verify -Dit.test=JwtSecretHermeticityIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false "-DJWT_SECRET=contaminado-…" "-Dapp.security.jwt.secret=contaminado-…"` (valores ≥ 32 bytes para que el fallo, si lo hay, sea de precedencia y no de validación).
- **Mutación**: (a) sin el registro de D2 el IT falla con las `-D` contaminantes; (b) si el mensaje de `JwtSecretRules` incluyera el valor, fallan `JwtSecretRulesTest`, `JwtSecretStartupTest` y `JwtSecretStartupOutputTest`. Ambas se revierten tras comprobarlas.
- **Regresión**: `./mvnw -B verify` verde con `Skipped: 0` (Docker Desktop encendido; sin Docker los `*IT` se saltan y no prueban nada, ver `docs/tooling-setup.md` §6).

### D4. Docs, ejemplo y migración
- `secrets.properties.example`: comentario "al menos **32 bytes** aleatorios (`openssl rand -base64 48`); la app no arranca si falta, si es este valor de ejemplo o si tiene menos de 32 bytes". El valor de ejemplo **no** cambia (lo vigila el test de sincronía).
- `readme.md`: tras el `cp`, generar `JWT_SECRET` propio.
- `docs/backend.md`: §11 nueva subsección con las reglas, por qué la validación está en `TokenService`, y dos reglas que mantienen la garantía (nunca loguear `SecurityProperties`/`Jwt` — su `toString()` incluye el secreto; nunca fijar `app.security.jwt.secret` con `@TestPropertySource`); corrige "`application-dev-secrets.yml`" (obsoleto) por `secrets.properties`. §12: `JWT_SECRET` obligatorio; **rotar invalida los access tokens emitidos pero no las sesiones** (el refresh token es opaco, se guarda hasheado y no depende del secreto: el frontend renueva el access de forma transparente); para expulsar a todos (secreto filtrado) hay que revocar también los refresh tokens (borrar `refresh_tokens`); con varias réplicas, todas con el mismo secreto.
- `docs/tooling-setup.md`: cómo regenerar a mano un `secrets.properties` con secreto corto o de ejemplo — sustituir la línea por `JWT_SECRET=` + la salida de `openssl rand -base64 48` (sin `openssl` en Windows: `node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"`). Sin abrir el archivo en la consola ni pegarlo en ningún sitio.

### D5. Aviso de backend listo (agregado durante el apply)
`infra.config.StartupReadyBanner`, portado de HSO: escucha `ApplicationReadyEvent` y escribe **una sola** entrada de log con un bloque delimitado, solo ASCII, porque la consola de Windows no usa UTF-8 por defecto.
- **URL base:** `http://localhost:<local.server.port><context-path>`. Sin servidor web (tests sin puerto) no hay `local.server.port`, así que no escribe nada.
- **Nombre:** `app.name`, si se define; si no, **Dentdesk** (es texto visible, así que va con el nombre del producto, CLAUDE.md).
- **Swagger:** OdontoRisas no tiene un interruptor `SWAGGER_ENABLED` como HSO; Swagger UI está activo por defecto. El aviso lee `springdoc.swagger-ui.enabled` (por defecto `true`) y muestra la URL o "desactivado". No se agrega un interruptor nuevo: eso sería otro cambio.
- **Sin secretos por construcción:** lee solo cuatro propiedades no sensibles. Un test lo fija con un secreto JWT y una contraseña de BD presentes en el entorno.

## Risks / Trade-offs

- [Un despliegue sin `JWT_SECRET` válido deja de arrancar] → intencionado (hoy arranca con un secreto público).
- [Un `secrets.properties` local copiado del ejemplo deja de arrancar] → mensaje explícito con el comando para generarlo; documentado en `tooling-setup.md` y en el PR.
- [Un log futuro de `SecurityProperties` filtraría el secreto (`toString()` del record)] → regla en §11; no se sobrescribe `toString()` en este change para mantenerlo un port fiel de HSO (candidato a endurecimiento aparte).
- [La validación depende de que exista `TokenService`] → siempre existe en la app; si algún día se firma con otro componente, debe llamar a `JwtSecretRules`.
- [Un secreto largo pero débil (`aaaa…`)] → no se mide entropía; el ejemplo y la doc guían a uno aleatorio.
- [Rotar el secreto / réplicas con secretos distintos] → consecuencias operativas documentadas en §12.

## Migration Plan

1. Antes de desplegar, confirmar que **cada entorno** define `JWT_SECRET` con ≥ 32 bytes aleatorios.
2. Si algún entorno estuvo desplegado **sin** la variable (o con el valor de ejemplo), sus tokens fueron falsificables: rotar el secreto **y** revocar los refresh tokens (borrar `refresh_tokens`).
3. En local, regenerar si el actual es corto o el de ejemplo (D4).

Rollback = revertir el PR (vuelve a arrancar con cualquier secreto, incluido el inseguro).

## Open Questions

- ¿Hay algún entorno de OdontoRisas desplegado hoy (staging/demo)? Si lo hay, confirmar su `JWT_SECRET` antes de mergear (Migration Plan 1-2). Lo decide y verifica el user; no bloquea el apply.
