# Proposal — add-cold-start-warmup

## Why

Dentdesk se desplegará en planes gratuitos: **Neon** (base), **Render** (backend) y **Vercel** (frontend), región Ohio (`docs/vision.md` §8, decisión del usuario 2026-10-10). Es el primero de cuatro changes: `add-cold-start-warmup` → `add-support-contact` → `add-cloud-deployment` → `add-database-backups`, el mismo camino que recorrió el proyecto hermano HSO.

- **Render free duerme el backend tras 15 min sin tráfico.** Despertarlo (arranque en frío de Spring Boot) tarda cerca de un minuto, y hoy la clínica vería "Cargando…" sin explicación todo ese tiempo.
- **Neon free suspende la base tras ~5 min sin actividad** y su cupo mensual de cómputo no alcanza para tenerla despierta 24/7: debe poder dormir. Hoy el pool de conexiones la mantendría despierta (10 conexiones retenidas y keepalive cada 2 min).
- **Actuator publica de más**: `/actuator/health` muestra el detalle (estado de la base, disco) a cualquiera, y `/actuator/info` es público sin que nada lo use.

## What Changes

- **Ping para el monitor externo**: el *liveness probe* de Actuator, `GET /actuator/health/liveness`. Responde `200` con `status: UP`, ya es público y no consulta la base. Un monitor gratuito (UptimeRobot o cron-job.org) lo llama cada 10 min y mantiene despierto el backend.
- **Actuator mínimo**: `show-details` pasa a `${HEALTH_SHOW_DETAILS:always}` (local igual; en Render, `never`). `/actuator/info` sale de `PublicPaths` y queda autenticado.
- **Pool que deja dormir a la base**: `minimum-idle: 0`, `idle-timeout` 60 s, `keepalive-time: 0` y máximo 5, configurables por variables `DB_POOL_*`.
- **Pantalla de arranque en frío** en el splash del `RootLayout`, que ya espera la primera petición al backend (el refresh de sesión):
  - **0–4 s**: como hoy ("Cargando…");
  - **4–90 s**: "Preparando tu consultorio digital", con una barra de progreso estimada que no llega al 100 % y la indicación de no cerrar la pestaña;
  - **más de 90 s**: "Está tardando más de lo normal" y botón **Reintentar**;
  - **sin internet**: un mensaje propio, porque el problema no es el servidor; reintenta solo al volver la red.

  Mismo marco que el login (panel de marca en escritorio, una columna en celular), solo tokens, modo oscuro, accesible y respetando "reducir movimiento".
- **Guía de despliegue** nueva, `docs/deployment.md`: el monitor externo, la conexión a Neon (cadena directa con `sslmode=require`, misma región), la condición de la cookie de sesión entre dominios y una **trampa propia de Dentdesk**: el rol de la aplicación en Neon debe crearse por SQL y sin `BYPASSRLS`, o el aislamiento entre clínicas (RLS) deja de existir en producción.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `project-foundation`: requisitos nuevos de ping de liveness, superficie pública de Actuator, pool que deja dormir a la base y pantalla de arranque en frío.

## Non-goals

- Configurar el despliegue en sí (Dockerfile, `render.yaml`, `vercel.json`, rutas `/ingresar` y `/registro`, cookie entre dominios): `add-cloud-deployment`.
- El contacto de soporte en la pantalla de espera: `add-support-contact` (aquí queda "avisa al administrador").
- Acelerar el arranque de Spring Boot (AOT, CDS): se mide y decide en `add-cloud-deployment`.
- Copias de seguridad: `add-database-backups`.
- Avisar de lentitud a mitad de sesión.

## Impact

- Backend: `application.yml` (probes, `show-details`, pool Hikari); `PublicPaths` sin `/actuator/info`; tests de health, Actuator y pool. Sin cambios de API ni del contrato OpenAPI (Actuator no está en él).
- Frontend: `RootLayout`; `ServerWarmupScreen` y `BrandPanel` (extraído de `AuthLayout`) en `core/components`; hooks genéricos `useElapsedWhile` y `useOnlineStatus` en `core/hooks`. Sin dependencias nuevas.
- Docs: `docs/deployment.md` (nuevo), `docs/backend.md` §11.1, `secrets.properties.example`, `docs/vision.md` §8.
- Sin migraciones ni cambios de dominio.
