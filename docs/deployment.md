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

- **Render free** suspende el backend tras **15 min sin tráfico entrante**. La siguiente petición lo despierta y espera el arranque de Spring Boot (~1 min). La app lo explica con la pantalla "Preparando tu consultorio digital" (`ServerWarmupScreen`).
- **Neon free** suspende el cómputo tras **~5 min sin actividad** y despierta en menos de un segundo. Su cupo de horas de cómputo al mes **no alcanza para tenerla despierta 24/7**: debe poder dormir. Por eso el pool del backend no retiene conexiones ni hace keepalive (`docs/backend.md` §11.1, variables `DB_POOL_*`).

## 2. Monitor externo (mantener despierto el backend)

El **único** mecanismo que evita el arranque en frío es un monitor externo gratuito que llame al backend:

- **URL**: `https://<servicio>.onrender.com/actuator/health/liveness` (público, responde `200` con `status: UP` y **no consulta la base**, así que no la despierta).
- **Frecuencia**: cada 10 min (menos de los 15 de Render).
- **Servicio**: UptimeRobot o cron-job.org (este último permite horarios, ver §3).
- El *health check* de Render (configurado a la misma ruta) **no lo sustituye**: Render no documenta que cuente como tráfico que evite la suspensión.
- **No** uses `/actuator/health` como ping: consulta la base en cada llamada y no la dejaría dormir.

## 3. Presupuesto de horas de Render (compartido con HSO)

Render free da **750 h de instancia al mes por workspace**. Un servicio despierto 24/7 consume ~730 h. **HSO ya está desplegado y despierto 24/7**, así que no queda cupo para que Dentdesk también lo esté en el mismo workspace. Opciones (decidir antes del primer despliegue, `add-cold-start-warmup` design D7):

1. **Workspace propio para Dentdesk** (otra cuenta o equipo de Render): cada uno con sus 750 h; ambos despiertos 24/7.
2. **Monitor solo en horario de atención** (cron-job.org): por ejemplo 7:00–22:00 hora de Perú son ~465 h al mes por servicio. Fuera de ese horario duermen y el primer ingreso muestra la pantalla de espera.
3. **Dejar dormir a Dentdesk** sin monitor hasta tener clínicas usándolo.

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
ALTER DATABASE <base> OWNER TO odontorisas_admin;
ALTER SCHEMA public OWNER TO odontorisas_admin;
GRANT CONNECT ON DATABASE <base> TO odontorisas_app;
GRANT USAGE ON SCHEMA public TO odontorisas_app;
```

> En Neon el `ALTER ... OWNER` puede exigir que el rol que lo ejecuta sea miembro del nuevo dueño
> (`GRANT odontorisas_admin TO <rol-dueño-del-proyecto>`). Se verifica y se fija el paso exacto en
> `add-cloud-deployment`, junto con la corrección de `modules/backend/scripts/db-roles.sql`
> (sus variables de `psql` no se reemplazan dentro del bloque `$$ … $$`).

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

Consecuencia: las páginas de acceso dejan `/auth/login` y `/auth/register` (que chocan con la API) y pasan a `/ingresar` y `/registro`.

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

## 7. Pendiente en los changes siguientes

- `add-support-contact`: WhatsApp y correo de soporte en login, registro y en "Está tardando más de lo normal".
- `add-cloud-deployment`: Dockerfile del backend (y medir memoria y arranque en 512 MB / 0,1 CPU), `render.yaml`, `vercel.json` con los rewrites, rutas `/ingresar` y `/registro`, roles de Neon (§4.2) y la matriz de verificación del despliegue.
- `add-database-backups`: copia diaria cifrada fuera de Neon con restauración verificada. "No caduca" no es una copia de seguridad.
