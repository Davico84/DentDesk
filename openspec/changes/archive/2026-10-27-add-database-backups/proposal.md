## Why

Las historias clínicas de Dentdesk viven solo en Neon (plan gratuito), y hay que conservarlas **20 años** (`ADR-0018`). "No caduca" no es una copia de seguridad: un borrado por error, una migración rota, un problema de la cuenta o un cambio del plan pueden perderlas, y la restauración a un punto anterior del plan gratuito cubre poco tiempo. Hoy no existe ninguna copia fuera de Neon ni una restauración probada.

Además lo exige la norma: el D.S. 016-2024-JUS **art. 48.1 num. 3** pide *"copias de respaldo seguras y continuas y verificación de integridad"* (`ADR-0019` D9), y la matriz de cumplimiento lo tiene en 🔴 **sin dueño técnico** (filas de respaldos y F9). `ADR-0014` y `ADR-0016` dejaron abierta la misma pregunta: *¿quién es el dueño técnico del cifrado de backups?*

Se postergó mientras Dentdesk estaba en fase de prueba (2026-10-12). Es requisito **antes de cargar pacientes reales**.

## What Changes

Se reusa el diseño de copias de HSOFACOP (en producción desde el 2026-10-08), con los ajustes que pide Dentdesk:

- **Copia diaria automática** con un workflow programado de GitHub Actions (más ejecución manual): `pg_dump` de la base de producción con un rol **de solo lectura**, **cifrada** con `age` a una clave pública **propia de Dentdesk** (no la de HSO); la clave privada queda solo con el responsable, fuera del repo y de GitHub. Se guarda como artifact con **90 días** de retención; la guía pide bajar una copia al mes a un disco propio.
- **Restauración verificada en cada copia**: antes de subirla, el job la restaura en una PostgreSQL 16 vacía y comprueba que la última migración del repo está aplicada y que están todas las tablas; si algo falla o no se puede verificar, el workflow falla y no sube nada.
- **Propio de Dentdesk — RLS forzada**: las tablas clínicas tienen `FORCE ROW LEVEL SECURITY`, y `pg_dump` **se niega a volcar** una tabla cuya policy filtraría filas. El rol de la copia necesita `BYPASSRLS` sin permisos de escritura, creado y verificado **por SQL** (como los roles de §4.2 de `docs/deployment.md`). Es lo primero que se prueba: si Neon no permite otorgarlo, el change queda bloqueado hasta decidir con el usuario.
- **Propio de Dentdesk — restaurar para reemplazar producción**: la copia se restaura sin dueños ni permisos; la guía incluye volver a dar los permisos a `odontorisas_app` y **correr la verificación de aislamiento entre clínicas** antes de apuntar la app a la base restaurada.
- **Sin configuración, no corre**: sin el secreto o la clave pública, el workflow omite la copia en verde.
- **Guía de copias y restauración** en `docs/deployment.md`.
- **Cumplimiento**: el dueño técnico de las copias queda asignado (este workflow y su guía); se actualizan la matriz y las Open Questions de `ADR-0014` y `ADR-0016`.
- Sin cambios de código de la aplicación, de contrato ni de esquema.

## Non-goals

- Cifrado en reposo de columnas clínicas (`ADR-0016` D5): es otro nivel, de la aplicación.
- Restauración automática sobre producción: siempre manual y a una base nueva.
- Copias cada pocos minutos o réplica: para eso queda la restauración a un punto anterior de Neon.
- Copias de Render o Vercel: no guardan estado, se reconstruyen desde el repo.
- Archivo pasivo y retención de 20 años: la copia protege contra pérdida, no es el archivo legal. Va en su propio change.

## Capabilities

### New Capabilities
<!-- ninguna -->

### Modified Capabilities
- `cloud-deployment`: nuevos requisitos "Copia diaria cifrada de la base fuera del proveedor" y "Restauración de la copia verificada".

## Impact

- Nuevo `.github/workflows/db-backup.yml`.
- Configuración manual, una vez (la hace el responsable, guiado):
  - rol de copia en Neon, por SQL, con `BYPASSRLS` y solo lectura;
  - secreto `BACKUP_DATABASE_URL` y variable `BACKUP_AGE_RECIPIENT` en GitHub;
  - par de claves `age` nuevo, generado en la PC del responsable.
- Cupo gratuito de GitHub para repos privados: artifacts de pocos MB (90 copias caben en los 500 MB) y unos 3 minutos de Actions por día.
- Docs: `docs/deployment.md` (copias y restauración), `docs/compliance/regulatory-matrix.md`, Open Questions de `ADR-0014` y `ADR-0016`, `docs/vision.md`.
- Sin cambios en backend, frontend, contrato ni esquema.
