> Commits separados por scope (`docs/commits.md`): backend · frontend · docs. Antes de tocar el frontend, skill `frontend-guard`; la pasada visual de la pantalla nueva, skill `frontend-design`. Los IT del backend usan Testcontainers: necesitan Docker Desktop encendido.

## 1. Backend

- [ ] 1.1 `application.yml`: `management.endpoint.health.probes.enabled: true` y `show-details: ${HEALTH_SHOW_DETAILS:always}` (design D1/D2)
- [ ] 1.2 `PublicPaths` sin `/actuator/info`; test que lo afirme (y que `/actuator/health/**` sigue público)
- [ ] 1.3 `application.yml`: `spring.datasource.hikari` con `minimum-idle: ${DB_POOL_MIN_IDLE:0}`, `idle-timeout: ${DB_POOL_IDLE_TIMEOUT_MS:60000}`, `keepalive-time: 0` y `maximum-pool-size: ${DB_POOL_MAX_SIZE:5}` (design D3); las variables nuevas en `secrets.properties.example` (sin abrir `secrets.properties`)
- [ ] 1.4 IT de liveness: sin sesión → `200`, `status` `UP`, sin `db`; con un `HealthIndicator` de prueba en `DOWN`, `/actuator/health` responde `503` y liveness sigue `200 UP`; `/actuator/info` sin sesión → `401`
- [ ] 1.5 IT con `management.endpoint.health.show-details=never`: `/actuator/health` sin `components`; y sin la variable, con `components` (scenario *Detalle visible en local*)
- [ ] 1.6 IT del pool: propiedades efectivas del `HikariDataSource` (0 / 0 / 60 000 / 5); con `idle-timeout` de 10 s solo en el test, tras abrir y devolver conexiones, esperar con Awaitility (`atMost` 90 s, por la revisión periódica de Hikari) hasta 0 conexiones; tras vaciar el pool, una consulta responde
- [ ] 1.7 Confirmar versiones de HikariCP y Awaitility con `mvn dependency:tree` y anotarlas en el design; `mvn verify` verde

## 2. Frontend

- [ ] 2.1 `core/hooks/useElapsedWhile(active)` y `core/hooks/useOnlineStatus()`, con tests (reinicio al pasar a inactivo; intervalo limpiado al desmontar; eventos `online`/`offline`)
- [ ] 2.2 `SmileArcBackground` a `core/components` y `BrandPanel` extraído de `AuthLayout`, **sin cambio visual** (capturas antes/después del login y el registro)
- [ ] 2.3 `core/components/ServerWarmupScreen`: estados `loading` / `warming` / `stuck` / `offline` con umbrales 4 / 45 / 90 s en constantes; barra `92 × (1 − e^(−s/22))` con `transform`; `BrandPanel` en escritorio y una columna en celular; solo tokens; "reducir movimiento"; región `status` que cambia solo por estado; foco en "Reintentar"; recarga al pulsar y al volver la red (design D5)
- [ ] 2.4 `RootLayout` renderiza `ServerWarmupScreen` mientras `status` es `idle`/`loading`
- [ ] 2.5 Tests, uno por scenario (`vi.useFakeTimers()`, refresh retenido con MSW, `window.location.reload` simulado): rápido sin pantalla; a los 4 s título y `progressbar` < 100; a los 90 s "Reintentar" con foco que recarga; respuesta después de 90 s; sin red y recarga al volver; éxito y error tras la espera; StrictMode envuelto explícitamente (una sola petición de refresh, la pantalla una vez, intervalos limpiados)
- [ ] 2.6 Pasada visual con `frontend-design` y capturas Playwright (refresh retenido con `page.route`) de `warming`, `stuck` y `offline`, en claro y oscuro, a 375 y 1280 px; `pnpm validate` verde

## 3. Docs

- [ ] 3.1 `docs/backend.md` §11.1: probes y liveness sin base, `HEALTH_SHOW_DETAILS`, `/actuator/info` autenticado, pool que no retiene conexiones ni hace keepalive
- [ ] 3.2 `docs/deployment.md` nuevo (design D6), enlazado desde `CLAUDE.md`: monitor externo, Neon (cadena directa, región, `DB_POOL_*`), **roles y RLS en Neon con la consulta de verificación de `rolbypassrls`**, variables de Render y la condición de la cookie entre dominios (se resuelve en `add-cloud-deployment`)
- [ ] 3.3 `docs/vision.md` §8: el ítem Despliegue con sus cuatro changes y este en progreso
- [ ] 3.4 `openspec validate add-cold-start-warmup --strict`
