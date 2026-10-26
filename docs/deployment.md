# Despliegue — Neon + Render + Vercel

> Guía viva. Se escribe a medida que avanzan los changes de despliegue (`docs/vision.md` §8):
> `add-cold-start-warmup` (este documento) → `add-support-contact` → `add-cloud-deployment` →
> `add-database-backups` (copias, §4.3 y §4.4).
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
  - **Despertar diario: cron-job.org dispara un workflow de GitHub Actions** (`.github/workflows/wake-up.yml`). El workflow despierta Dentdesk y HSO con `curl` (hasta 3 intentos de 150 s) y falla, con aviso de GitHub por correo, si alguna no responde. ~1 min de Actions por día. **Verificado el 2026-10-12**: Dentdesk dormida despertó en **63 s**.
  - **El horario de GitHub (`schedule`) no sirve como despertador.** GitHub corre los programados solo desde la rama por defecto (`dev`) y los atrasó **4 a 6 horas** (13/10: 14:57; 14/10: 13:09, en vez de 8:45). Queda en el workflow solo como respaldo.
  - **Lo que sí dispara a tiempo: un job de cron-job.org que llama a la API de GitHub** (`workflow_dispatch`, que arranca en segundos), lunes a sábado **8:45** `America/Lima`:
    - URL: `https://api.github.com/repos/Davico84/Odontorisas/actions/workflows/wake-up.yml/dispatches`, método `POST`.
    - Encabezados (filas Key/Value): `Authorization: Bearer <token>`, `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2022-11-28`, `Content-Type: application/json`.
    - **Cuerpo**: solo `{"ref":"dev"}`. Respuesta esperada: **204**. Un 422 es un cuerpo mal cargado (p. ej., los encabezados pegados en el cuerpo); 401/403, el token.
    - **Token**: *fine-grained*, solo el repo `Odontorisas`, permiso **Actions: Read and write** y nada más. Guardado solo en cron-job.org. **Vence**: anotar la fecha y renovarlo antes, o el despertador deja de funcionar sin aviso (cron-job.org mostrará 401).
    - **Verificado el 2026-10-15**: *Test run* → 204 → el workflow arrancó en segundos y despertó HSO en **54 s** y Dentdesk en **74 s** (ambas dormidas).
    - Un solo job despierta las dos apps: el workflow tiene un trabajo por servicio.
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

> ⚠️ **Después del `ALTER DATABASE … OWNER`, el SQL Editor de Neon entra como el dueño de la base**
> (`odontorisas_admin`), no como el dueño del proyecto, y su selector solo cambia de base, no de rol.
> `odontorisas_admin` no puede crear roles. Para crear o tocar roles hay que conectarse como
> `dentdeskdb_owner` desde un cliente externo (psql de Docker o Beekeeper, URL directa del botón
> *Connect* con ese rol).

### 4.3 Copias de la base (`add-database-backups`)

Una copia diaria **fuera de Neon**, cifrada y verificada por restauración antes de guardarse. Workflow:
`.github/workflows/db-backup.yml` (script de verificación: `.github/scripts/db-backup-verify.sh`).

| | |
|---|---|
| Cuándo | todos los días a las 08:00 UTC (03:00 en Lima) y a pedido (*Actions → DB backup → Run workflow*) |
| Qué | `pg_dump` completo de la base de producción, con el rol `odontorisas_backup` |
| Verificación | antes de guardar, la restaura en una PostgreSQL 16 vacía: la última migración del repo está aplicada y están todas las tablas de la copia. Si algo falla o no se puede verificar, el workflow queda rojo y no guarda nada (GitHub avisa por correo) |
| Cifrado | `age` con la clave pública de la variable `BACKUP_AGE_RECIPIENT`. Solo la clave privada la descifra |
| Dónde | artifact `db-backup-<AAAA-MM-DD>` del workflow, **90 días** |
| Configuración | secreto `BACKUP_DATABASE_URL` + variable `BACKUP_AGE_RECIPIENT`. Si falta alguno, la copia se omite en verde |

**El rol de la copia salta RLS y no escribe.** Las tablas clínicas tienen RLS forzada, y `pg_dump` **falla** si una policy le escondería filas: la copia sale completa o no sale. Por eso `odontorisas_backup` lleva `BYPASSRLS` (lee todas las clínicas) y solo `pg_read_all_data` (no escribe nada). Se crea conectado como `dentdeskdb_owner` (ver el aviso de §4.2), que tiene `BYPASSRLS` propio:

```sql
-- Contraseña larga y aleatoria, solo letras y números (va dentro de una URL). Fuera del repo.
CREATE ROLE odontorisas_backup LOGIN NOSUPERUSER BYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD '<clave-backup>';
GRANT CONNECT ON DATABASE dentdeskdb TO odontorisas_backup;
GRANT pg_read_all_data TO odontorisas_backup;
```

**Verificación obligatoria** (hecha el 2026-10-26). La primera consulta: `rolbypassrls` en `true` y el resto en `false`. La segunda: todas las `*_alguna` y `crea_en_public` en `false` y `lee_todas` en `true`:

```sql
SELECT rolname, rolsuper, rolbypassrls, rolcreaterole, rolcreatedb,
       pg_has_role('odontorisas_backup', 'neon_superuser', 'member')    AS hereda_neon_superuser,
       pg_has_role('odontorisas_backup', 'pg_write_all_data', 'member') AS escribe
FROM pg_roles WHERE rolname = 'odontorisas_backup';

SELECT
  bool_or(has_table_privilege('odontorisas_backup', c.oid, 'INSERT'))   AS insert_alguna,
  bool_or(has_table_privilege('odontorisas_backup', c.oid, 'UPDATE'))   AS update_alguna,
  bool_or(has_table_privilege('odontorisas_backup', c.oid, 'DELETE'))   AS delete_alguna,
  bool_or(has_table_privilege('odontorisas_backup', c.oid, 'TRUNCATE')) AS truncate_alguna,
  bool_or(pg_has_role('odontorisas_backup', c.relowner, 'MEMBER'))      AS dueno_alguna,
  bool_and(has_table_privilege('odontorisas_backup', c.oid, 'SELECT'))  AS lee_todas,
  has_schema_privilege('odontorisas_backup', 'public', 'CREATE')        AS crea_en_public
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p');
```

> Para probar permisos, mejor esta consulta que intentar escrituras: cubre todas las tablas, y en
> clientes como Beekeeper un intento fallido deja la transacción abortada y tapa el resultado de la
> siguiente prueba.

El secreto `BACKUP_DATABASE_URL` es la URL libpq **directa** del rol (sin `-pooler`):
`postgresql://odontorisas_backup:<clave>@<host-directo>/dentdeskdb?sslmode=require`. **Nunca** la de `odontorisas_app`: la RLS la filtra y el volcado falla (a propósito).

**La clave privada** (`dentdesk-backup.key`, generada con `age-keygen` en la PC del responsable, **propia de Dentdesk**, distinta de la de HSO) está en **dos lugares**: Bitwarden (nota segura) y un disco externo. Nunca entra a GitHub ni al repo. Si se pierden las dos, las copias quedan ilegibles para siempre.

**Supuestos operativos** (si cambia alguno, las copias pueden parar sin aviso):
- el repositorio sigue **privado** (si pasa a público, GitHub desactiva los programados tras 60 días sin actividad y los logs quedan a la vista);
- `dev` sigue siendo la rama por defecto: los programados corren desde ahí;
- Actions sigue habilitado en el repositorio.

**Cupo gratuito del repo privado** (500 MB de artifacts, 2.000 min/mes de Actions). Medido en la primera copia (2026-10-26, base en V29): artifact de **304 KB** y job de **51 s**, o sea ≈ 27 MB (5 %) y ≈ 26 min/mes (1,3 %). La cuenta: `tamaño del artifact × 90` contra 500 MB y `minutos por corrida × 30` contra 2.000. Al pasar el **80 %** de cualquiera de los dos, se baja la retención o se mueve la copia a otro almacenamiento, en un change. Si el cupo se llena, la subida falla y el workflow queda rojo. Uso actual: *Settings → Billing and plans*.

**Revisión mensual** — el **primer día hábil de cada mes**, quien opera el despliegue:
1. Descarga la copia más reciente (*Actions → DB backup → último run verde → Artifacts*, o `gh run download <id>`) y la guarda en el disco externo. Así se conserva más allá de los 90 días.
2. Comprueba que descifra y que se restaura completa con el script (desde `modules/backend`):
   `.\scripts\verify-backup-restore.ps1 -RunId <id> -KeyPath <ruta de dentdesk-backup.key>`. Restaura en un PostgreSQL 16 local como rol no superusuario y compara con producción (pide la URL de `odontorisas_backup`, sin mostrarla): conteos por tabla, permisos, función de V24, huella de RLS y aislamiento. Solo imprime PASA / FALLA y borra todo lo descifrado al terminar. Si hubo actividad en producción después de la copia, los conteos pueden diferir sin que sea un error.
3. Mira la fecha del último artifact (si no es de ayer o de hoy, el programado dejó de correr) y el uso del cupo.

### 4.4 Restaurar una copia

**Descifrar** (en la PC del responsable, con la clave privada):

```powershell
gh run download <run-id> -n db-backup-<AAAA-MM-DD>
age -d -i dentdesk-backup.key -o db.dump db-backup-<AAAA-MM-DD>.dump.age
```

El volcado descifrado tiene historias clínicas en claro: se borra al terminar y nunca se sube a ningún lado.

**Caso 1 — revisar datos** (lo normal). PostgreSQL 16 local en Docker, nunca producción. Para **verificar** una copia alcanza con el script de la revisión mensual (§4.3); a mano, para mirar los datos:

```powershell
docker run -d --name restore-db -e POSTGRES_PASSWORD=restore -p 5433:5432 postgres:16
docker run --rm --network host -v "${PWD}:/backup" postgres:16 pg_restore --no-owner --no-acl --exit-on-error --dbname=postgresql://postgres:restore@localhost:5433/postgres /backup/db.dump
```

(En Windows sin red `host` en Docker, usar una red de Docker compartida y el nombre del contenedor como host.)

**Caso 2 — reemplazar producción.** Solo si la base de producción se perdió o se dañó. La copia va **sin dueños ni permisos** (`--no-owner --no-acl`): restaurarla "tal cual" deja a `odontorisas_app` sin acceso o, peor, a la app conectada con un rol que salta RLS, y entonces **una clínica ve los datos de otra**. En orden:

1. En Neon, una **rama nueva** desde `production` (*Branches → New branch*). En ella, una base vacía cuyo dueño sea `odontorisas_admin`, creada **conectado como `dentdeskdb_owner`** desde un cliente externo (el SQL Editor entra como `odontorisas_admin`, que no puede crear bases): `CREATE DATABASE restored OWNER odontorisas_admin;`. El host de la rama es distinto del de producción (*Connect* con la rama elegida, sin pooling).

   Los pasos 2 a 4 los hace y verifica el script, que se niega a restaurar sobre una base que no esté vacía:
   `.\scripts\verify-backup-restore.ps1 -RunId <id> -KeyPath <clave> -Remote -RemoteDatabase restored` (pide la URL de `odontorisas_backup` de producción, el host de la rama y las claves de `odontorisas_admin` y `odontorisas_app`). A mano:
2. Restaurar **conectado como `odontorisas_admin`**, así las tablas y la función `SECURITY DEFINER` de `V24` quedan con el mismo dueño que en producción:
   `pg_restore --no-owner --no-acl --exit-on-error --dbname=<URL directa de odontorisas_admin en la rama> db.dump`
3. Devolver los permisos a la app, como `odontorisas_admin`. Flyway **no** vuelve a correr `V6` (la tabla de migraciones restaurada ya está al día) y `ALTER DEFAULT PRIVILEGES` solo cubre tablas futuras:
   ```sql
   GRANT CONNECT ON DATABASE <base> TO odontorisas_app;
   GRANT USAGE ON SCHEMA public TO odontorisas_app;
   GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO odontorisas_app;
   GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO odontorisas_app;
   ALTER DEFAULT PRIVILEGES FOR ROLE odontorisas_admin IN SCHEMA public
     GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO odontorisas_app;
   ALTER DEFAULT PRIVILEGES FOR ROLE odontorisas_admin IN SCHEMA public
     GRANT USAGE, SELECT ON SEQUENCES TO odontorisas_app;
   ```
4. Verificar, todo con el resultado esperado, **antes** de tocar Render:
   ```sql
   -- (a) La app puede todo lo que necesita: sin filas. El CASE evita que PostgreSQL evalúe la función
   --     sobre otro tipo de relación antes de filtrar (has_sequence_privilege falla con una tabla TOAST).
   SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
   WHERE n.nspname = 'public'
     AND CASE WHEN c.relkind IN ('r', 'p') THEN NOT has_table_privilege('odontorisas_app', c.oid, 'SELECT, INSERT, UPDATE, DELETE')
              WHEN c.relkind = 'S' THEN NOT has_sequence_privilege('odontorisas_app', c.oid, 'USAGE, SELECT')
              ELSE false END;

   -- (b) Función de V24: dueño odontorisas_admin, prosecdef true, proconfig {search_path=public}.
   SELECT r.rolname AS dueno, p.prosecdef, p.proconfig
   FROM pg_proc p JOIN pg_roles r ON r.oid = p.proowner
   WHERE p.proname = 'current_user_is_org_admin';

   -- (c) RLS intacta: huella de TODAS las tablas (también las que no tienen RLS), con sus flags y
   --     cada policy con su comando, roles (ordenados) y expresiones; un NULL se marca aparte para no
   --     confundirlo con texto vacío. Tiene que ser IGUAL a la de la referencia.
   SELECT md5(string_agg(f.fila, E'\n' ORDER BY f.fila)) AS huella_rls
   FROM (
     SELECT concat_ws('|', c.relname, c.relrowsecurity, c.relforcerowsecurity,
                      COALESCE(p.policyname, '<SIN_POLICY>'), COALESCE(p.cmd, '<NULL>'),
                      COALESCE((SELECT string_agg(r, ',' ORDER BY r) FROM unnest(p.roles) AS r), '<NULL>'),
                      COALESCE(p.qual, '<NULL>'), COALESCE(p.with_check, '<NULL>')) AS fila
     FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
     LEFT JOIN pg_policies p ON p.schemaname = 'public' AND p.tablename = c.relname
     WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
   ) f;
   ```
   **Referencia** para la huella: la misma consulta en producción, si sigue accesible; si no, en una base local migrada por Flyway desde el repo **a la misma versión** que la copia (el backend local contra una PostgreSQL vacía). Si las huellas difieren, correr solo la subconsulta `f` en las dos bases y comparar las filas: una tabla con RLS en `false`, una policy que falta o una expresión cambiada (p. ej. `USING (true)`) bloquean el reemplazo. Y la verificación de roles de §4.2 (`odontorisas_app` sin `rolbypassrls` ni `neon_superuser`).
5. Levantar el backend **local** contra la rama y entrar con una cuenta real. En una terminal nueva, en `modules/backend`, **una línea por vez** (si se pega todo junto, cada `Read-Host` toma la línea siguiente del pegado como respuesta):
   ```powershell
   $h = Read-Host "Host de la rama"
   $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR((Read-Host -AsSecureString "Clave de odontorisas_app")))
   $env:DB_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR((Read-Host -AsSecureString "Clave de odontorisas_admin")))
   $env:DB_URL = "jdbc:postgresql://$h/restored?sslmode=require"; $env:DB_USERNAME = "odontorisas_app"; $env:DB_ADMIN_USERNAME = "odontorisas_admin"
   mvn spring-boot:run
   ```
   (JDBC **sin** `channel_binding`.) Esperado: Flyway `Successfully validated N migrations` y `Schema "public" is up to date`, y con el frontend local se entra y se ven los datos de la clínica. El aislamiento ya lo comprobó el script (paso anterior); si se quiere verlo en la app, con dos clínicas cada una ve solo lo suyo (si producción tiene una sola, crear una de prueba **en la rama**).
6. Recién entonces, apuntar `DB_URL` de Render a la rama restaurada y **redesplegar** el servicio (*Manual Deploy* o reinicio): una instancia vieja puede conservar conexiones a la base anterior. Después, `/api/actuator/health/liveness` en `200 UP` y un login con una cuenta real para confirmar que la app lee la base nueva.

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
| `MAIL_ENABLED` | `true` | sin correo, **toda invitación se rechaza con 503** (add-platform-admin D7) |
| `MAIL_HOST` / `MAIL_PORT` | `smtp-relay.brevo.com` / **`2525`** | **Render free bloquea la salida a 25, 465 y 587** (desde 2025-09): con 587 el envío se cuelga. Brevo ofrece 2525 como alternativa; verificado en producción el 2026-10-15 |
| `MAIL_TIMEOUT` | sin definir (`PT10S`) | tope de conexión SMTP: un puerto bloqueado da 503 rápido en vez de colgar la petición |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | credenciales SMTP de Brevo | secreto; es la clave SMTP, no la del buzón |
| `MAIL_FROM` | remitente verificado en Brevo | |
| `FRONTEND_URL` | `https://dentdeskapp.vercel.app` | base del enlace del correo de invitación |
| `PLATFORM_ADMIN_*` | solo durante el bootstrap | ver §11.1; **se retiran** después |

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
7. **Despertar diario**: job de cron-job.org que dispara `.github/workflows/wake-up.yml` por la API de GitHub (lun–sáb 8:45 Lima), con un token *fine-grained* de alcance mínimo (§3). Probarlo con *Test run* tras 20 min sin uso: 204 y una ejecución nueva en Actions.
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
- El rol dueño del proyecto en Neon se llama `dentdeskdb_owner`. El SQL Editor ya **no** entra con él, sino con `odontorisas_admin` (ver el aviso de §4.2).
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
| Sesión en Safari / iOS | **no probada**: no hay un iPhone disponible (2026-10-12). Riesgo bajo: la cookie es de primera parte (mismo dominio de Vercel, vía el proxy), que es lo que Safari no bloquea. Probar cuando haya un dispositivo o un usuario con iPhone |
| Arranque en frío | backend dormido → liveness en **63,6 s** (2026-10-11), **74 s** (2026-10-15), bajo los 120 s del proxy de Vercel ✅. **No medido entrando por la app** (login): queda cubierto por el despertar de las 8:45 y por el aviso "Despertando el servidor…" con reintento (`update-slow-request-notice`) |
| Monitor | cron-job.org lun–sáb 9:00–21:00 **no despierta** el servicio dormido (Render le devuelve una página HTML, con o sin `Accept: application/json`), solo lo mantiene despierto. El despertar lo hace `wake-up.yml`, **disparado por cron-job.org vía la API de GitHub** porque el `schedule` de GitHub se atrasó 4–6 h: con las dos apps dormidas, HSO **54 s** y Dentdesk **74 s → `UP`** ✅ (2026-10-15) |
| Soporte | botones de WhatsApp y correo desde el login publicado: abren el chat y el correo ✅ (2026-10-12) |

Datos de la verificación en producción: las dos clínicas de prueba (`Verificación A` y `Verificación B`, correos `verificacion-*@dentdesk.test`) ya no existen; al 2026-10-26 producción tiene una sola organización.

## 10. Pendiente en los changes siguientes

- ~~`add-support-contact`~~ (hecho): una fila compacta "¿Problemas para entrar?" con botones de WhatsApp y correo bajo el formulario de login y registro, y en "Está tardando más de lo normal". Los datos están en `modules/frontend/src/modules/core/config/supportContact.ts`; quedan públicos en la web.
- ~~`add-cloud-deployment`~~ (archivado 2026-10-15): puesto en marcha (§8) y verificado (§9). Quedan **sin probar**, con riesgo bajo y documentados en la matriz: la sesión en Safari/iOS y el arranque en frío medido entrando por el login.
- ~~`add-database-backups`~~ (2026-10-26): copia diaria cifrada fuera de Neon con restauración verificada (§4.3 y §4.4). Era requisito **antes de cargar pacientes reales**: sin copia, un borrado o una corrupción en Neon pierde historias clínicas que hay que conservar 20 años (ADR-0018).

## 11. Operación de plataforma: administrador global y alta de clínicas

Desde `add-platform-admin` el registro público no existe: cada clínica nace de un **`PLATFORM_ADMIN`**, que la
aprovisiona y emite la invitación de su primer `ADMIN`. **No hay pantalla**: el `PLATFORM_ADMIN` opera solo
por la API. El login del frontend lo rechaza a propósito, porque no tiene membresía en ninguna clínica.

### 11.1 Crear el `PLATFORM_ADMIN` (una vez por entorno)

No hay endpoint de alta: lo crea el backend **al arrancar**, si lo piden las variables de entorno.

1. **Requisito:** el código de `add-platform-admin` ya está en la rama que despliega el entorno (`main` en
   producción) y el correo funciona (`MAIL_*` y `FRONTEND_URL` de §6). Sin correo no se puede invitar a nadie.
2. Elegir el **correo** del `PLATFORM_ADMIN`. Es solo el usuario de login: no recibe nada y puede no tener
   buzón. **No** puede ser el correo de una cuenta de clínica (el backend no arranca) y conviene que no sea
   uno que luego quieras usar como administrador de una clínica: una identidad, un correo.
3. Generar una **clave** larga. En PowerShell:
   `$b = New-Object byte[] 18; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); ([Convert]::ToBase64String($b)) -replace '[+/=]','x'`.
   Guardarla en el gestor de contraseñas. Va **en claro**: el backend la guarda como hash BCrypt.
4. En el panel de Render, en las variables del servicio:

   | Variable | Valor |
   |---|---|
   | `PLATFORM_ADMIN_BOOTSTRAP_ENABLED` | `true` |
   | `PLATFORM_ADMIN_EMAIL` | el correo del paso 2 |
   | `PLATFORM_ADMIN_FULL_NAME` | p. ej. `Plataforma Dentdesk` |
   | `PLATFORM_ADMIN_PASSWORD` | la clave del paso 3 |

   Guardar redespliega. Esperar a que el servicio esté *Live*.
5. Comprobar el login con el script de §11.2: debe decir "Sesión de plataforma iniciada". Se puede cancelar
   en la confirmación sin crear nada.
6. **Retirar el secreto:** `PLATFORM_ADMIN_BOOTSTRAP_ENABLED=false` y **borrar** `PLATFORM_ADMIN_PASSWORD`.
   Guardar, lo que redespliega otra vez. La clave sigue valiendo: ya está en la base.

Reglas del bootstrap: si el correo ya es `PLATFORM_ADMIN`, no hace nada, **tampoco cambia la clave**. Si
pertenece a una cuenta de clínica, el backend **no arranca** y lo dice en el log. Hoy no hay forma de cambiar
la clave de un `PLATFORM_ADMIN` ya creado (open question de `add-platform-admin`).

En **local** es lo mismo con `secrets.properties` (ver `secrets.properties.example`) y reiniciando el backend.

### 11.2 Dar de alta una clínica

Con Windows PowerShell, desde `modules/backend`:

```powershell
# Producción: la API se llama a través del proxy de Vercel
.\scripts\new-clinic.ps1 -BaseUrl https://dentdeskapp.vercel.app/api `
  -PlatformEmail <correo-platform-admin> -ClinicName "Clínica Sonrisas" -AdminEmail <correo-del-dueño>

# Local: -BaseUrl por defecto es http://localhost:8080/api
.\scripts\new-clinic.ps1 -PlatformEmail admin@dentdesk.local -ClinicName "Clínica Prueba" -AdminEmail ana@correo.com
```

El script pide la clave por teclado (no queda en el historial), inicia sesión y **pide confirmación antes de
crear la clínica**, porque envía un correo real. No guarda el token.

- **Producción dormida:** el primer pedido puede tardar ~60 s (§1). Si se corta, repetir: la segunda vez el
  backend ya está despierto. Abrir antes la app o `/api/actuator/health/liveness` también lo despierta.
- **El invitado tiene 10 minutos** para ingresar el código desde que sale el correo. Avisarle antes de crear la
  clínica. Si vence, hoy no se puede reenviar: hay que crear otra clínica con otro correo y borrar la
  primera a mano.
- Si la política de ejecución bloquea el script: `powershell -ExecutionPolicy Bypass -File .\scripts\new-clinic.ps1 ...`.

| Respuesta | Qué pasó | Qué hacer |
|---|---|---|
| 401 en el login | correo o clave incorrectos, no es `PLATFORM_ADMIN`, o bloqueado 15 min tras 5 fallos | revisar; si no existe, §11.1 |
| 405 | la petición salió como GET (navegador) | usar el script o un POST |
| 409 | el correo del administrador ya pertenece a una cuenta | usar otro correo (en Gmail, `nombre+clinica@gmail.com`) |
| 503 | el correo no se pudo enviar, o `MAIL_ENABLED` no es `true` | no se creó nada; revisar `MAIL_*` (en Render, `MAIL_PORT=2525`) y reintentar |
| 502 tras ~2 min | sin `MAIL_TIMEOUT` (versión anterior a los timeouts) y puerto SMTP bloqueado | no se creó nada; usar `MAIL_PORT=2525` |
| 400 | nombre vacío, de 1 carácter o de más de 120; correo inválido | corregir |

