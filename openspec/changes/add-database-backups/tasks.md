# Tasks

> Rama `apply-database-backups` desde `dev`, PR a `dev`. Sin código de la app (ni backend ni frontend): el
> "test" de cada Scenario es una ejecución del workflow o una restauración, anotada abajo. Commits por scope:
> `ci` · `docs`. Las tareas 2.x las hace el responsable en Neon, su PC y GitHub (guiado). Nunca se muestran ni
> se commitean contraseñas, la URL de conexión ni la clave privada.

## 1. Workflow

- [ ] 1.1 `.github/workflows/db-backup.yml` sobre el de HSO (design D1): `runs-on: ubuntu-24.04`, `schedule` 08:00 UTC + `workflow_dispatch` (sin `pull_request` ni `inputs`), `permissions: contents: read`, `concurrency` sin cancelar; `actions/checkout` (sparse, solo migraciones) y `actions/upload-artifact` fijadas por SHA; `PG_IMAGE` = `postgres:16@sha256:<digest>` tomado al implementar (mismo digest en el servicio, comentario con el comando y la fecha)
- [ ] 1.2 Paso de configuración: sin `BACKUP_DATABASE_URL` o sin `BACKUP_AGE_RECIPIENT` → "Copia omitida: falta configuración" en el resumen y el resto se salta, en verde
- [ ] 1.3 Volcado `pg_dump --format=custom --no-owner --no-acl` **sin** `--enable-row-security` (design D3: si RLS filtraría, falla); URL solo por `env` y `-e DATABASE_URL`; errores traducidos a un tipo de falla sin host ni usuario (host desconocido, credenciales, base o rol inexistente, permisos, **RLS** → "el rol de la copia no salta RLS", sin respuesta, genérico); `test -s` del volcado
- [ ] 1.4 Servicio `restore` + `pg_restore --no-owner --no-acl --exit-on-error` y comprobaciones fail-closed sin imprimir datos (última `V<n>` del repo por `sort -n` contra `max(version::numeric)` con `success`; cada `TABLE` del índice del volcado en la base restaurada; falla sin directorio de migraciones, sin `V<n>__*.sql`, sin `flyway_schema_history` con versiones o sin tablas). Las comprobaciones en un script de shell del paso, ejecutable también en local para la tarea 3.7
- [ ] 1.5 `age` de apt (`age --version` en el log), cifrado con `age -r`, borrado del volcado sin cifrar, subida `db-backup-<AAAA-MM-DD>` con `retention-days: 90`, `compression-level: 0` y **solo el `.age`** en `path`; limpieza `if: always()` de `$RUNNER_TEMP/backup`
- [ ] 1.6 `actionlint` del workflow sin errores

## 2. Configuración (responsable, guiado)

- [ ] 2.1 **Bloqueante.** Neon, SQL Editor con el dueño del proyecto: crear `odontorisas_backup` con `BYPASSRLS` + `pg_read_all_data` (design D3). Verificar en `pg_roles`: `rolbypassrls` true; `rolsuper`, `rolcreaterole`, `rolcreatedb`, hereda `neon_superuser` y hereda `pg_write_all_data` en false. Conectado como `odontorisas_backup`, fallan por permisos: `INSERT`, `UPDATE`, `DELETE` y `TRUNCATE` en `patients`, `CREATE TABLE` en `public`, `ALTER TABLE` y `DROP TABLE` de `patients` *(Credencial que no escribe)*. **Si Neon rechaza `BYPASSRLS`, parar: el change queda bloqueado y se decide con el usuario**
- [ ] 2.2 Instalar `age` en la PC y generar un par **nuevo** para Dentdesk (`age-keygen -o dentdesk-backup.key`); guardar la clave privada en dos lugares fuera del repo
- [ ] 2.3 GitHub: secreto `BACKUP_DATABASE_URL` (URL directa de `odontorisas_backup`, sin `-pooler`, `sslmode=require`) y variable `BACKUP_AGE_RECIPIENT` (clave pública)

## 3. Verificación

> 3.1–3.4 son ejecuciones con `workflow_dispatch` después del merge a `dev` (la rama por defecto); se anota en
> cada una el número de run. Los secretos de prueba se vuelven al valor correcto al terminar cada una.

- [ ] 3.1 Configuración parcial, **dos corridas**: sin el secreto y con la variable; con el secreto y sin la variable → las dos omitidas en verde, con el aviso en el resumen *(Proyecto sin configurar)*
- [ ] 3.2 Fallas de conexión, una corrida cada una: host inexistente, contraseña incorrecta, base inexistente → rojo, sin artifact, paso de limpieza ejecutado, y el log dice solo el tipo de falla, sin host, usuario ni URL *(Falla de la copia · Logs sin secretos ni datos · Limpieza tras una falla)*
- [ ] 3.3 Con la URL de `odontorisas_app` (rol que RLS filtra) → rojo con "el rol de la copia no salta RLS", sin artifact *(Credencial que RLS filtraría)*
- [ ] 3.4 Configurado, a pedido → verde, artifact `db-backup-<fecha>` con 90 días; el artifact descargado contiene **solo** el `.age` (ningún `db.dump`); el log muestra la restauración y comprobaciones ok sin URL ni datos *(Copia a pedido · Copia que se restaura)*. Al día siguiente, una corrida **programada** en verde *(Copia diaria)*. Anotar tamaño del artifact y duración del job (design D5)
- [ ] 3.5 Restaurar ese artifact en PostgreSQL 16 local siguiendo la guía, **como un rol no superusuario** (design D4): sin la clave privada, `age -d` falla *(Artifact sin la clave privada)*; con ella, la base tiene las dos clínicas de verificación con su paciente cada una y los **conteos por tabla** coinciden con los de producción tomados a la misma hora con `odontorisas_backup` (solo se comparan, no se guardan) *(Filas de todas las clínicas · Restauración en una base nueva)*
- [ ] 3.6 En el índice del volcado (`pg_restore --list`), para `patients` y `consultation_note`: tabla → datos → `ROW SECURITY` → `POLICY`, en ese orden (design D4)
- [ ] 3.7 Casos fail-closed **en local**, con el script de verificación de 1.4 contra una PostgreSQL 16 de Docker: directorio de migraciones inexistente; directorio sin `V<n>__*.sql`; copia sin `flyway_schema_history`; copia atrasada (un `V30__x.sql` de prueba en una copia del directorio); copia con una tabla quitada de la restauración (`pg_restore -L` con la línea comentada); orden numérico `V9`/`V10`/`V29` → cada uno falla con su mensaje y sin datos en la salida *(Copia que no se restaura · Sin referencia para verificar)*
- [ ] 3.8 En la base local de 3.5, aplicar los permisos de la guía como el dueño y correr las verificaciones de design D4: privilegios de `odontorisas_app` en todas las tablas y secuencias, función de V24 (dueño, `SECURITY DEFINER`, `search_path=public`), `relrowsecurity`/`relforcerowsecurity` y `organization_isolation` en cada tabla clínica, y con `app.organization_id` de una clínica no se ve el paciente de la otra
- [ ] 3.9 **Rama de Neon**: restaurar el artifact en una rama nueva siguiendo la guía de reemplazo (base vacía de `odontorisas_admin`, restauración como él, permisos, verificaciones de 3.8 y de §4.2), levantar el backend **local** contra esa rama con `odontorisas_app` y comprobar el aislamiento entre las dos clínicas de verificación; **Render no se toca**. Borrar la rama al terminar *(Reemplazo de producción)*

## 4. Docs

- [ ] 4.1 `docs/deployment.md`: sección de copias (qué, dónde, retención, rol `odontorisas_backup` y su verificación, clave privada en dos lugares, **supuestos operativos** —repo privado, `dev` por defecto, Actions habilitado—, cupo medido con la cuenta y el umbral del 80 %, **revisión mensual** con fecha, responsable y comprobación de descifrado) y guía de restauración con los dos casos de design D4 (permisos explícitos y verificaciones); actualizar el aviso del encabezado y §10
- [ ] 4.2 `docs/compliance/regulatory-matrix.md`: filas de respaldos (art. 48.1 num. 3) y F9 con este change como dueño y su estado; GitHub como sub-encargado con copias cifradas
- [ ] 4.3 Open Questions de `ADR-0014` (4) y `ADR-0016` (2): resueltas para las copias, con enlace; el cifrado en reposo de columnas sigue abierto
- [ ] 4.4 `docs/vision.md` §8: marcar "Gestión de cuentas" (ya la hizo `add-staff`); al archivar, `add-database-backups` ✅
- [ ] 4.5 `openspec validate add-database-backups --strict` y medir a mano los requirements (≤ 500 caracteres)
