# Design

## Context

- **Producción**: Neon Free (PostgreSQL 16, `aws-us-east-2`, conexión directa con `sslmode=require`; el cómputo se suspende tras ~5 min sin uso). Backend en Render, frontend en Vercel (`docs/deployment.md`).
- **Datos de salud de varias clínicas** en la misma base, aisladas por Row-Level Security. Una copia expuesta es más grave que una copia perdida, y una copia **incompleta que parece buena** es peor que ninguna.
- **RLS forzada**: las tablas clínicas tienen `ENABLE` + `FORCE ROW LEVEL SECURITY` con la policy `organization_isolation`. Aplica también al dueño de las tablas (`odontorisas_admin`): por eso falló el deploy de V23 (`docs/deployment.md`, PR #90).
- **Roles en Neon** (`docs/deployment.md` §4.2): `odontorisas_admin` (dueño, Flyway) y `odontorisas_app` (`NOBYPASSRLS`), creados **por SQL**. Los roles creados desde el panel heredan `neon_superuser`, que **salta RLS y puede escribir**.
- **Repositorio privado**, rama por defecto `dev`. Los logs y artifacts no son públicos, pero el cupo gratuito es de 500 MB de artifacts y 2.000 minutos de Actions al mes.
- **Precedente**: HSOFACOP tiene este mismo change en producción desde el 2026-10-08 (`D:/proyectos/HSOFACOP/openspec/changes/archive/2026-10-08-add-database-backups/`, `.github/workflows/db-backup.yml`). Se reusa su diseño; acá solo se justifica lo que cambia.
- **Norma**: D.S. 016-2024-JUS art. 48.1 num. 3 (copias seguras, continuas y con verificación de integridad) y art. 52 (cifrado y checksum al sacar datos fuera de las instalaciones) — `ADR-0019` D9. Dentdesk es el **encargado** (`ADR-0019` D1).

## Goals / Non-Goals

**Goals:**
- Una copia diaria fuera de Neon, ilegible sin una clave que solo tiene el responsable.
- Que la copia tenga **todas las filas de todas las clínicas**, o que falle.
- Verificada por restauración antes de guardarla.
- Una guía para recuperar que no rompa el aislamiento entre clínicas.

**Non-Goals:** ver `proposal.md`.

## Decisions

### D1 — Mismo diseño que HSO

Se copian tal cual, con nombres genéricos, las decisiones de HSO:
- workflow `db-backup.yml`, `schedule` diario 08:00 UTC (03:00 en Lima) + `workflow_dispatch`, sin `pull_request`;
- `permissions: contents: read`, `concurrency` sin cancelar en curso, `runs-on: ubuntu-24.04`;
- solo `actions/checkout` (sparse, solo las migraciones) y `actions/upload-artifact`, fijadas por SHA;
- `pg_dump --format=custom --no-owner --no-acl` desde `postgres:16` **fijado por digest** (el mismo digest en el servicio de restauración);
- la URL solo por `env`, nunca en `run:`; los errores de `pg_dump` se traducen a un tipo de falla sin host ni usuario;
- restauración en un servicio `postgres:16` efímero y comprobaciones **fail-closed**: última migración `V<n>` del repo (orden numérico) contra `max(version::numeric)` aplicada con éxito, y cada `TABLE` del índice del volcado presente en la base restaurada;
- cifrado `age -r` con la clave pública como **variable** (no secreta), borrado del volcado sin cifrar, artifact `db-backup-<AAAA-MM-DD>` con 90 días;
- paso de configuración que omite la copia en verde, y limpieza `if: always()`.

El digest de `postgres:16` se toma de nuevo al implementar (no se copia el de HSO): `docker buildx imagetools inspect postgres:16`.

### D2 — Clave `age` propia de Dentdesk (decisión del usuario, 2026-10-26)

Un par nuevo, distinto del de HSO: si se filtra una clave, solo quedan expuestas las copias de un producto. La clave privada se guarda en **dos lugares** fuera del repo (p. ej. un gestor de contraseñas y un disco propio); sin ella las copias son inútiles. Con `age` el runner solo puede cifrar: ni quien controle el repo puede descifrar las copias.

### D3 — Rol de copia con `BYPASSRLS` y solo lectura

`pg_dump` corre con `row_security = off` por defecto: si una policy fuera a filtrar filas de una tabla, **falla** con *"query would be affected by row-level security policy"* en vez de volcar menos. Eso es justo lo que pide el spec (copia completa o falla), así que no se agrega `--enable-row-security`.

Para que el volcado pase, el rol de copia tiene que saltar RLS:

```sql
-- Desde el SQL Editor de Neon, con el rol dueño del proyecto. La contraseña, larga y aleatoria, fuera del repo.
CREATE ROLE odontorisas_backup LOGIN NOSUPERUSER BYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD '<clave-backup>';
GRANT CONNECT ON DATABASE <base> TO odontorisas_backup;
GRANT pg_read_all_data TO odontorisas_backup;
```

Verificación obligatoria (va a la guía, junto a la de §4.2):

```sql
SELECT rolname, rolsuper, rolbypassrls,
       pg_has_role('odontorisas_backup', 'neon_superuser', 'member') AS hereda_neon_superuser,
       pg_has_role('odontorisas_backup', 'pg_write_all_data', 'member') AS escribe
FROM pg_roles WHERE rolname = 'odontorisas_backup';
```

`rolbypassrls` en `true`; `rolsuper`, `hereda_neon_superuser` y `escribe` en `false` (y `rolcreaterole`, `rolcreatedb` en `false`, consultados en la misma fila). Conectado como `odontorisas_backup`, **cada** escritura falla por permisos: `INSERT`, `UPDATE`, `DELETE` y `TRUNCATE` en `patients`, `CREATE TABLE` en `public`, y `ALTER`/`DROP` de una tabla existente. Un solo `INSERT` no prueba "solo lectura". `pg_read_all_data` no concede escritura y `BYPASSRLS` solo quita el filtro de RLS.

- **Por qué no un rol del panel de Neon**: hereda `neon_superuser`, que salta RLS pero **también escribe** y crea roles. Una credencial de copia filtrada no debe poder borrar historias.
- **Por qué no `odontorisas_admin`**: es el dueño de las tablas (escribe y altera el esquema) y la RLS forzada lo filtra igual.
- **Riesgo a verificar primero (tarea 2.1, bloqueante)**: en PostgreSQL 16 solo un rol con `BYPASSRLS` puede crear otro con `BYPASSRLS`. Neon documenta que los roles del proyecto pertenecen a `neon_superuser` (con `CREATEROLE` y `BYPASSRLS`) y que los creados por SQL no reciben esa membresía, así que debería funcionar; no está probado en este proyecto.
- **Si Neon lo rechaza, el change queda bloqueado** y se decide con el usuario. No hay sustituto equivalente. La única alternativa conocida (policies de lectura `USING (true)` para el rol de copia + `pg_dump --enable-row-security --inserts`, porque `COPY` no restaura bien con RLS) es **menos robusta**: con RLS activa, `pg_dump` vuelca lo visible sin fallar, así que una tabla nueva sin la policy, o una policy restrictiva, saldría incompleta en silencio. Solo sería aceptable con inventario de las tablas con RLS, una policy no filtrante comprobada en cada una y conteos por tabla iguales entre origen y restauración. Queda como último recurso, no como plan B.

### D4 — Restaurar para reemplazar producción sin romper el aislamiento

La copia va sin dueños ni permisos (`--no-owner --no-acl`), así que una restauración "tal cual" deja una base donde `odontorisas_app` no puede leer nada o, peor, donde se restauró con un rol que salta RLS y la app se conecta con él. La guía separa dos casos:

- **Revisar datos** (lo normal): PostgreSQL 16 local en Docker, `pg_restore --no-owner --no-acl`. Nunca toca producción.
- **Reemplazar producción**: en una **rama nueva de Neon**, crear una base vacía cuyo dueño sea `odontorisas_admin` y restaurar **conectado como `odontorisas_admin`**, así las tablas y la función `SECURITY DEFINER` de V24 quedan con el mismo dueño que en producción. Después, los permisos y las verificaciones de abajo, y recién entonces apuntar `DB_URL` de Render a esa rama.

**Permisos explícitos tras `--no-acl`.** Flyway no vuelve a correr `V6` (la tabla de migraciones restaurada ya está en la última versión), y `ALTER DEFAULT PRIVILEGES` solo cubre objetos futuros. La guía trae los comandos, ejecutados como `odontorisas_admin`:

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

`EXECUTE` de las funciones queda en el valor por defecto (`PUBLIC`): ninguna migración lo revoca, así que `--no-acl` no lo pierde.

**Verificaciones antes de apuntar Render**, todas consultas con resultado esperado (en la guía):
- permisos de `odontorisas_app` en cada tabla y secuencia (`has_table_privilege` / `has_sequence_privilege` sobre todas las de `public`), sin ninguna en `false`;
- la función de V24: dueño `odontorisas_admin`, `prosecdef` en `true` y `proconfig` con `search_path=public` (consulta a `pg_proc`);
- cada tabla clínica sigue con `relrowsecurity` y `relforcerowsecurity` en `true` y con su policy `organization_isolation` (`pg_class` + `pg_policies`), comparado contra producción;
- la verificación de roles de §4.2 y la de aislamiento entre clínicas de §9.

**Orden de restauración.** `pg_dump` deja `ENABLE/FORCE ROW LEVEL SECURITY` y las policies en *post-data*, después de las filas, así que restaurar como `odontorisas_admin` no queda filtrado por su propia RLS. No se asume: se comprueba en el índice del volcado (`pg_restore --list`) que, para una tabla clínica, el orden es tabla → datos → RLS → policy, y restaurando como un rol no superusuario (tareas 3.5 y 3.8).

### D5 — Repo privado: cupo y programados

- **Cupo, medido y no supuesto**: la estimación (pocos MB por copia, unos minutos por corrida) se reemplaza en la guía por lo medido en la primera copia real: tamaño del artifact y duración del job. La guía trae la cuenta (`tamaño × 90` contra 500 MB; `minutos × 30` contra 2.000) y un umbral: al pasar el **80 %** de cualquiera de los dos, se baja la retención o se mueve la copia a otro almacenamiento, en un change. `retention-days: 90` no garantiza que 90 copias quepan: si el cupo se llena, la subida falla, el workflow queda rojo y GitHub avisa por correo.
- **Programados**: corren desde la **rama por defecto, `dev`**. El workflow llega a `dev` con el PR del apply y la verificación (tareas 3.x) se hace desde ahí. GitHub atrasó el `schedule` de `wake-up.yml` 4 a 6 horas: para una copia de madrugada no importa.
- **Supuestos operativos**, escritos en la guía porque si alguno cambia las copias pueden parar sin aviso: el repo sigue **privado** (si pasa a público, vuelve la desactivación de programados tras 60 días sin actividad y los logs quedan públicos), `dev` sigue siendo la rama por defecto y Actions sigue habilitado.
- **Revisión mensual**, con fecha fija (el primer día hábil del mes) y responsable (quien opera el despliegue): bajar la copia más reciente a un disco propio, **comprobar que descifra** y que `pg_restore --list` la lee, mirar la fecha del último artifact (detecta un programado que dejó de correr) y el uso del cupo. Sin esta revisión, los 90 días no cubren la conservación prolongada.
- Logs privados **no** relaja la higiene: se mantiene todo lo de D1. Un repo puede volverse público o compartirse.

### D6 — Dueño técnico de las copias

Se cierra la pregunta abierta de `ADR-0014` (OQ 4) y `ADR-0016` (OQ 2): el dueño técnico de las **copias** es este workflow y su guía en `docs/deployment.md`, operado por el responsable del despliegue. No hace falta un ADR nuevo: la decisión no sobrevive al change más allá de la guía. El **cifrado en reposo** de columnas (`ADR-0016` D5) sigue abierto y no es de acá.

- **GitHub como sub-encargado**: guarda las copias, pero cifradas con una clave que no tiene; Neon y Render ya lo son en claro. Se anota en la matriz junto a la fila de respaldos; la autorización de sub-encargados de nube es la pregunta legal de `ADR-0019` (Regl. art. 31.1), dueño el usuario.
- `age` autentica el contenido (un archivo alterado no descifra): cubre el *checksum de verificación* del art. 52 para la copia.

## Risks / Trade-offs

- **Neon no permite crear el rol con `BYPASSRLS` por SQL** → el change queda bloqueado y se decide con el usuario (D3).
- **Conteos no comparados en el workflow**: el log no imprime datos, y contar en el origen después del volcado no usa el mismo instante. La completitud la garantiza `pg_dump` (falla si RLS filtraría). Los conteos por tabla se comparan **una vez**, a mano, en la verificación (tarea 3.5).
- **Limpieza tras fallas en restauración o cifrado**: el paso `if: always()` es el mismo para cualquier falla. Se ejecuta de verdad una falla de volcado (3.2) y una de verificación (3.7, local); el resto se revisa sobre el workflow, sin inducir fallas con entradas del `workflow_dispatch`, que serían superficie nueva.
- **Perder la clave privada** deja las copias inservibles → dos lugares, lo exige la guía.
- **90 días en GitHub** → lo anterior depende de la copia mensual manual a un disco propio.
- **Una copia al día** → se puede perder hasta un día si no alcanza la restauración a un punto anterior de Neon.
- **Riesgo aceptado** (como en HSO): un commit malicioso en `dev` podría leer el secreto o el volcado antes del cifrado. Un responsable, sin colaboradores con escritura; si se suman, mover el workflow a un repo privado aparte.
- **Pruebas**: un workflow no tiene tests de unidad. Se verifica ejecutándolo (sin configurar, con secreto inválido, con un rol que RLS filtra, configurado) y restaurando una copia en local.

## Migration Plan

Solo un workflow nuevo y docs. Orden: rol de copia en Neon (2.1, bloqueante) → claves y configuración de GitHub → merge a `dev` → ejecuciones de verificación → restauración local → restauración de prueba en una rama de Neon (borrada al terminar). Rollback: desactivar o borrar el workflow; las copias ya guardadas siguen sirviendo con la clave privada.

## Open Questions

Ninguna bloqueante para proponer. La viabilidad de `BYPASSRLS` en Neon se resuelve en la tarea 2.1.
