# Despliegue — Neon + Render + Vercel

> Guía viva. Se escribe a medida que avanzan los changes de despliegue (`docs/vision.md` §8):
> `add-cold-start-warmup` (este documento) → `add-support-contact` → `add-cloud-deployment` →
> `add-database-backups`. **Hasta `add-cloud-deployment` el sistema no es desplegable de extremo a
> extremo** (falta la imagen del backend, la configuración de los servicios y la cookie entre
> dominios, §5).
>
> Las cifras de los planes gratuitos son de **octubre de 2026** y cambian: revísalas al desplegar.

| Pieza | Servicio | Plan | Región |
|---|---|---|---|
| Base de datos | Neon (PostgreSQL) | Free | `aws-us-east-2` (Ohio) |
| Backend | Render (Docker) | Free | Ohio |
| Frontend | Vercel | Hobby | (CDN global) |

Base y backend en la **misma región**: cada consulta cruza esa distancia.

## 1. Cómo duermen y despiertan los servicios

- **Render free** suspende el backend tras **15 min sin tráfico entrante**. La siguiente petición lo despierta y espera el arranque de Spring Boot (~1 min). La app lo explica de dos formas: al **abrir o recargar**, con la pantalla "Preparando tu consultorio digital" (`ServerWarmupScreen`); **durante el uso** (p. ej. una pestaña que quedó abierta), con el aviso "Despertando el servidor…" (`SlowRequestNotice`) cuando cualquier petición tarda más de 4 s.
- **Neon free** suspende el cómputo tras **~5 min sin actividad** y despierta en menos de un segundo. Su cupo de horas de cómputo al mes **no alcanza para tenerla despierta 24/7**: debe poder dormir. Por eso el pool del backend no retiene conexiones ni hace keepalive (`docs/backend.md` §11.1, variables `DB_POOL_*`).

## 2. Monitor externo (mantener despierto el backend)

El **único** mecanismo que evita el arranque en frío es un monitor externo gratuito que llame al backend:

- **URL**: `https://<servicio>.onrender.com/api/actuator/health/liveness` (la API vive bajo `/api`; público, responde `200` con `status: UP` y **no consulta la base**, así que no la despierta).
- **Frecuencia**: cada 10 min (menos de los 15 de Render).
- **Servicio**: UptimeRobot o cron-job.org (este último permite horarios, ver §3).
- El *health check* de Render (configurado a la misma ruta) **no lo sustituye**: Render no documenta que cuente como tráfico que evite la suspensión.
- **No** uses `/actuator/health` como ping: consulta la base en cada llamada y no la dejaría dormir.

## 3. Presupuesto de horas de Render (compartido con HSO)

Render free da **750 h de instancia al mes por workspace**. Un servicio despierto 24/7 consume ~730 h, y **HSO ya está desplegado y despierto 24/7**.

**Decisión (usuario, 2026-10-11): Dentdesk y HSO comparten workspace, las dos despiertas de lunes a sábado, de 9:00 a 21:00, hora de Perú.** Fuera de ese horario (noches y domingos) duermen y el primer ingreso muestra la pantalla "Preparando tu consultorio digital" (~1 min; medido en Render: 64 s).

- **Monitor: cron-job.org**, un job por app, zona `America/Lima`, días **lunes a sábado**, horas **9 a 20**, minutos **0, 10, 20, 30, 40, 50** (último ping 20:50; el servicio queda despierto hasta ~21:05, porque Render lo suspende 15 min después del último tráfico). La última hora marcada es la **20**, no la 21: marcar la 21 extiende el horario a las 22:00 y deja el margen en ~34 h.
  - Dentdesk: `https://dentdesk-api.onrender.com/api/actuator/health/liveness`
  - HSO: `https://hs-facop-api.onrender.com/actuator/health/liveness` (sin `/api`)
  - Aviso de fallo después de 3 fallos seguidos: a las 9:00 el primer ping puede superar los 30 s que espera cron-job.org mientras el servidor despierta.
  - **Encabezado `Accept: application/json`** en cada job (Avanzado → Encabezados). Hallazgo del 2026-10-12: sin él, con el servicio **dormido**, Render le responde a cron-job.org en ~1 s con una página HTML grande ("salida demasiado grande") y **no despierta** el servicio; todos los pings de 9:00 a 10:20 fallaron así y las dos apps siguieron dormidas. Con el servicio despierto el ping funciona igual con o sin el encabezado. `curl` (que no pide HTML) sí lo despierta: tardó 63 s y devolvió el JSON.
  - **El encabezado no alcanzó** (prueba del mismo día tras 20 min de inactividad: mismo fallo). La diferencia con `curl` parece ser el origen de la petición (servidores de datacenter), no las cabeceras. Conclusión: cron-job.org **no despierta** el servicio, solo lo mantiene despierto; el encabezado se deja porque no estorba.
  - **Despertar diario: GitHub Actions** (`.github/workflows/wake-up.yml`): lunes a sábado 8:45 Lima despierta Dentdesk y HSO (hasta 3 intentos de 150 s) y falla, con aviso de GitHub por correo, si alguna no responde. Se ejecuta también a mano (*Run workflow*). ~1 min de Actions por día. cron-job.org queda para **mantenerlas** despiertas de 9:00 a 21:00. **Verificado el 2026-10-12** (*Run workflow* en `main`, cron de Dentdesk pausado y >20 min sin uso): Dentdesk dormida despertó al primer intento en **63 s** con `{"status":"UP"}`; HSO respondió en 0 s (su cron la mantenía despierta).
- **UptimeRobot (el monitor 24/7 que tenía HSO) se pausa**: sin horario en el plan gratuito, despertaría a HSO de noche y agotaría el cupo compartido.
- **Presupuesto** (peor mes: 27 días hábiles): ~12 h 15 min al día por app ≈ **331 h cada una, ~662 h entre las dos**, de 750. **Margen: ~88 h** para el uso fuera de horario.
- **Cada ingreso fuera de horario cuesta** ~17 min (arranque + los 15 min hasta la suspensión): el margen alcanza para unos 300 al mes entre las dos apps. Si se agota, Render suspende **los dos** servicios hasta el mes siguiente.
- Alternativas evaluadas (lun–sáb): 9–20 (~142 h de margen), 8–20 (~88 h), 8–21 (~44 h, justo), 9–22 (~34 h, muy justo). Todos los días 9–20: ~52 h. Un workspace propio para Dentdesk se descartó (otra cuenta que mantener).
- El horario de HSO se cambia en su propio repo (change OpenSpec en HSOFACOP), con estos mismos valores.

## 4. Base de datos en Neon

### 4.1 Conexión

- En `DB_URL` va la cadena **directa** (sin `-pooler` en el host) con `sslmode=require`:
  `jdbc:postgresql://<host>.us-east-2.aws.neon.tech/<base>?sslmode=require`.
- No el endpoint `-pooler`: es PgBouncer en modo transacción. Flyway necesita semántica de sesión para sus locks, y el contexto de RLS (`app.user_id`, `app.organization_id`) se fija por transacción; no se depende del pooler sin una prueba específica.

### 4.2 ⚠️ Roles y RLS (propio de Dentdesk)

El aislamiento entre clínicas depende de **Row-Level Security**, y RLS **no aplica** a un rol `SUPERUSER` o con `BYPASSRLS`. En Neon, **los roles creados desde el panel heredan `neon_superuser`, que tiene `BYPASSRLS`**. Si el rol de la aplicación se crea así, **cada clínica podría leer los datos de las otras**, y nada falla a la vista.

Por eso los dos roles se crean **por SQL**, desde el SQL Editor de Neon, conectado con el rol dueño del proyecto (no con `odontorisas_app`):

```sql
-- Reemplaza las dos contraseñas por valores largos y aleatorios (no las guardes en el repo).
CREATE ROLE odontorisas_admin LOGIN PASSWORD '<clave-admin>';
CREATE ROLE odontorisas_app LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD '<clave-app>';
```

Luego la propiedad de la base y del esquema pasa al admin (Flyway crea las tablas como admin) y la app recibe acceso de conexión. Las tablas y secuencias las concede la migración `V6` al rol `odontorisas_app`:

```sql
GRANT odontorisas_admin TO CURRENT_USER;
ALTER DATABASE <base> OWNER TO odontorisas_admin;
ALTER SCHEMA public OWNER TO odontorisas_admin;
GRANT CONNECT ON DATABASE <base> TO odontorisas_app;
GRANT USAGE ON SCHEMA public TO odontorisas_app;
```

> El `GRANT odontorisas_admin TO CURRENT_USER` es necesario en Neon: desde PostgreSQL 16, crear un
> rol no hace miembro de él a quien lo crea, y sin esa membresía el `ALTER ... OWNER` falla (el
> dueño del proyecto de Neon no es superusuario). Lo mismo hace `modules/backend/scripts/db-roles.sql`
> con `psql`, probado contra un PostgreSQL 16 local como superusuario (`DbRolesScriptIT`). **En Neon se verifica a mano**: el test no reproduce sus restricciones.

**Verificación obligatoria antes de abrir la app** — `odontorisas_app` debe tener las dos columnas en `false`:

```sql
SELECT rolname, rolsuper, rolbypassrls,
       pg_has_role('odontorisas_app', 'neon_superuser', 'member') AS hereda_neon_superuser
FROM pg_roles
WHERE rolname LIKE 'odontorisas_%';
```

Si `rolbypassrls` es `true` o `hereda_neon_superuser` es `true` para `odontorisas_app`, **no despliegues**: borra el rol y vuelve a crearlo por SQL.

La app usa `odontorisas_app` (`DB_USERNAME` / `DB_PASSWORD`); Flyway, `odontorisas_admin` (`DB_ADMIN_USERNAME` / `DB_ADMIN_PASSWORD`).

## 5. Condición previa: la cookie de sesión entre dominios

El refresh token viaja en una cookie `HttpOnly; Secure; SameSite=Lax`, que es además la protección CSRF del backend. `*.vercel.app` y `*.onrender.com` son *sites* distintos (ambos sufijos están en la Public Suffix List): con `SameSite=Lax` el navegador **no envía** la cookie y la sesión no se restaura al recargar. `withCredentials` y CORS no lo compensan.

Solución elegida (se configura en `add-cloud-deployment`): **servir la API bajo el mismo origen que el frontend** con los *rewrites* de Vercel (`/api/*` y `/auth/*` hacia Render). La cookie queda de primera parte y la protección actual se mantiene. Vercel Hobby espera hasta **120 s** en un rewrite externo, más que el arranque en frío. Descartadas: Netlify (corta a 26 s) y `SameSite=None` (debilita CSRF y Safari bloquea las cookies de terceros).

Consecuencia (decidida en `add-cloud-deployment`): **toda la API pasa a `/api`** (`/api/auth/login`, `/api/patients`, `/api/actuator/health/liveness`), porque sin prefijo sus rutas chocan con las pantallas de la app (`/patients/1` es pantalla y endpoint). Vercel reenvía solo `/api/*`. Las pantallas `/auth/login` y `/auth/register` **no cambian** (en HSO se renombraron; aquí ya no chocan).

## 6. Variables del backend en Render

| Variable | Valor | Nota |
|---|---|---|
| `DB_URL` | cadena directa de Neon (§4.1) | secreto |
| `DB_USERNAME` / `DB_PASSWORD` | `odontorisas_app` | secreto |
| `DB_ADMIN_USERNAME` / `DB_ADMIN_PASSWORD` | `odontorisas_admin` | secreto, solo Flyway |
| `JWT_SECRET` | ≥ 32 bytes aleatorios (`openssl rand -base64 48`) | secreto; sin él no arranca |
| `HEALTH_SHOW_DETAILS` | `never` | no publicar el estado de la base |
| `SWAGGER_ENABLED` | `false` (o ausente) | |
| `COOKIE_SECURE` | `true` | |
| `CORS_ALLOWED_ORIGINS` | origen exacto del frontend | se fija en `add-cloud-deployment` |
| `DB_POOL_*` | sin definir (valores por defecto) | solo si se mide otra cosa |

Los secretos se cargan en el panel de Render, **nunca** en el repo.

## 7. Imagen del backend: medición local

`modules/backend/Dockerfile` (multi-etapa, JRE 25, usuario `app`, AOT cache de Java 25 entrenado en el build). Medido el 2026-10-11 con `docker run -m 512m --cpus 0.1` (los límites de Render free) contra un PostgreSQL 16 local:

| Caso | Hasta liveness `200` | Spring "Started" | Memoria |
|---|---|---|---|
| Base vacía (22 migraciones) | 76 s | 65,9 s | 331 MiB |
| Base ya migrada (rearranque) | 100 s | — | 303 MiB |

- Los dos quedan bajo los **120 s** que el proxy de Vercel espera un rewrite, pero **con poco margen** (la CPU de 0,1 varía entre corridas). Si en Render se acerca al límite, el primer pedido tras dormir puede cortar; la pantalla de arranque en frío ofrece "Reintentar" y la segunda vez el backend ya está despierto. Medir de nuevo en Render (tarea 4.1 de `add-cloud-deployment`).
- Imagen inspeccionada: sin `secrets.properties`, `.env`, `src/` ni `mvnw`; corre como `app`, no `root`.
- Para probarla en local: `docker build -t dentdesk-api:local modules/backend` y `docker run -m 512m --cpus 0.1 -e PORT=10000 -e DB_URL=... -e DB_USERNAME=... -e DB_PASSWORD=... -e JWT_SECRET=... -e COOKIE_SECURE=false -p 10000:10000 dentdesk-api:local` (sobre HTTP, la cookie `Secure` no vuelve: `COOKIE_SECURE=false` solo para probar login en local).

## 8. Puesta en marcha (una vez, en orden)

Los pasos en paneles los hace el responsable; el detalle de cada uno está en las secciones de arriba.

1. **Neon**: crear el proyecto en `aws-us-east-2`. Crear los dos roles **por SQL** y correr la verificación de §4.2 (`odontorisas_app` sin `rolsuper`, `rolbypassrls` ni `neon_superuser`). Copiar la cadena **directa** (sin `-pooler`) con `sslmode=require`.
2. **Render**: en el workspace compartido con HSO, *New Blueprint* desde el repo, rama `main` (lee `render.yaml`). Cargar `DB_URL`, `DB_USERNAME`/`DB_PASSWORD` (`odontorisas_app`), `DB_ADMIN_USERNAME`/`DB_ADMIN_PASSWORD` (`odontorisas_admin`), `JWT_SECRET` (`openssl rand -base64 48`) y `CORS_ALLOWED_ORIGINS=https://pendiente.invalid`. Primer deploy: Flyway migra con el admin y `/api/actuator/health/liveness` responde `200`.
3. **Comprobar en Neon** (SQL Editor) que las tablas son de `odontorisas_admin` y que `odontorisas_app` puede leerlas. **No abrir la app a usuarios antes.**
4. Si Render le dio otra URL que `dentdesk-api.onrender.com`, corregirla en `vercel.json` (commit en `dev`, PR a `main`).
5. **Vercel**: importar el repo, *Root Directory* = raíz, rama de producción `main`, sin `VITE_API_URL` (lee `vercel.json`). Copiar el origen de producción (`https://<app>.vercel.app`) a `CORS_ALLOWED_ORIGINS` en Render, sin `/` final.
6. **cron-job.org**: `GET https://<servicio>.onrender.com/api/actuator/health/liveness` cada 10 min, lunes a sábado, de 9:00 a 20:50 (`America/Lima`), §3. Mantiene despierto el servicio, pero **no lo despierta**.
7. **Despertar diario**: `.github/workflows/wake-up.yml` ya corre solo desde `main` (lun–sáb 8:45 Lima); probarlo una vez con *Run workflow* tras 20 min sin uso, §3.
8. **Verificar** con la matriz de `add-cloud-deployment` (design D7) y anotar los resultados aquí, incluida la prueba de que **dos clínicas no se ven entre sí**.

Producción = rama `main`: Render despliega solo con los checks del CI en verde; los pushes a `dev` generan *previews* de Vercel sin sesión (CORS solo admite el origen de producción).

## 9. Despliegue de producción: estado y verificación

**Publicado el 2026-10-11.**

| Pieza | Dónde |
|---|---|
| Frontend | `https://dentdeskapp.vercel.app` (Vercel; `odontorisas-frontend.vercel.app` redirige) |
| Backend | `https://dentdesk-api.onrender.com` (Render free, Ohio, workspace compartido con HSO) |
| Base | Neon, proyecto `dentdesk`, base **`dentdeskdb`**, `aws-us-east-2` |
| `CORS_ALLOWED_ORIGINS` | `https://dentdeskapp.vercel.app` |

Notas de la puesta en marcha (para la próxima vez):
- `DB_URL` va en formato **JDBC** (`jdbc:postgresql://<host>/dentdeskdb?sslmode=require`), **sin** usuario ni clave dentro y sin `channel_binding`. Pegar la cadena de Neon tal cual falla con `'url' must start with "jdbc"`.
- En Render, el **valor** de cada variable es el dato, no el nombre de la variable (un `DB_ADMIN_USERNAME=DB_ADMIN_USERNAME` dio `password authentication failed for user 'DB_ADMIN_USERNAME'`).
- Neon crea en la base una tabla de ejemplo `playing_with_neon`: Flyway se niega a migrar un esquema no vacío. Se borra (`DROP TABLE public.playing_with_neon;`) antes del primer deploy. **Nunca** usar `baselineOnMigrate`.
- El rol dueño del proyecto en Neon (el del SQL Editor) se llama `dentdeskdb_owner`.
- `dentdesk.vercel.app` estaba tomado por otro proyecto: los `.vercel.app` son globales.

### Matriz de verificación (design D7 de `add-cloud-deployment`)

| Área | Resultado (2026-10-11) |
|---|---|
| Base: roles | `odontorisas_app` sin `rolsuper`, `rolbypassrls` ni `neon_superuser`; puede conectar y usar `public` ✅ |
| Base: migración | 22 migraciones; tablas de `odontorisas_admin`; app con DML en `patients`; RLS forzada ✅ |
| **Aislamiento** | dos clínicas registradas en la URL pública, un paciente cada una: cada una ve solo el suyo; pedir el de la otra → `404` ✅ |
| Rutas | recargar `/auth/login`, `/auth/register`, `/patients/1` → la app, no JSON ✅ |
| Proxy | `/api/actuator/health/liveness` vía Vercel → `200 UP`; `/actuator/*` no se reenvía (sirve la app) ✅ |
| Cookie | `refresh_token` del dominio de Vercel, `Path=/api/auth`, `Secure`, `HttpOnly`, `SameSite=Lax` ✅ |
| Sesión (Chrome) | registro → Inicio en ~4–10 s; recargar con sesión, en Inicio y en Pacientes ✅ |
| CORS | `POST /api/auth/refresh` vía proxy con el `Origin` de Vercel → `401` (sin cookie), no `403` ✅ |
| Actuator | liveness `200 UP` / health `200` sin detalle / info `401` / sin prefijo `404` / api-docs `404` ✅ |
| Smoke E2E | `E2E_BASE_URL=https://dentdeskapp.vercel.app` `auth.smoke` + `register.smoke`: 5/5 ✅ |
| Sesión en Safari / iOS | pendiente (prueba manual en un iPhone) |
| Arranque en frío | backend dormido → `/api/actuator/health/liveness` respondió en **63,6 s** (bajo los 120 s del proxy de Vercel) ✅; falta medirlo entrando por la app (login) |
| Monitor | cron-job.org lun–sáb 9:00–21:00 **no despierta** el servicio dormido (Render le devuelve una página HTML, con o sin `Accept: application/json`), solo lo mantiene despierto. El despertar lo hace GitHub Actions (`wake-up.yml`, 8:45): con Dentdesk dormida, **63 s → `UP`** ✅ (2026-10-12); falta ver la primera ejecución programada (2026-10-13 8:45) |
| Soporte | pendiente: abrir los botones de WhatsApp y correo desde el login publicado |

Datos de la verificación en producción: quedaron dos clínicas de prueba (`Verificación A` y `Verificación B`, correos `verificacion-*@dentdesk.test`, dominio que no recibe correo) con un paciente cada una. Son inofensivas; se pueden borrar con un script probado antes en local.

## 10. Pendiente en los changes siguientes

- ~~`add-support-contact`~~ (hecho): una fila compacta "¿Problemas para entrar?" con botones de WhatsApp y correo bajo el formulario de login y registro, y en "Está tardando más de lo normal". Los datos están en `modules/frontend/src/modules/core/config/supportContact.ts`; quedan públicos en la web.
- `add-cloud-deployment`: hecho en el repo (API bajo `/api`, Dockerfile medido en §7, `render.yaml`, `vercel.json`); falta la puesta en marcha de §8 y su matriz de verificación.
- `add-database-backups`: copia diaria cifrada fuera de Neon con restauración verificada. "No caduca" no es una copia de seguridad.
