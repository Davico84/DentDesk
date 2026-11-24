## Context

Ver proposal.md (Why). Lo que condiciona el diseño:

- **Aislamiento** por organización con RLS forzada (`docs/backend.md` §8.1): toda tabla de negocio lleva
  `organization_id`, índice y política `organization_isolation`; las FK hacia otras tablas de negocio son
  **compuestas por tenant** (`patients` y `consultation_note` ya exponen `UNIQUE (organization_id, id)`).
- `organization_settings` (1:1 con la organización) **no tiene RLS**: es metadato del tenant, como la zona
  horaria (`timezone`, por defecto `America/Lima`). Se crea al dar de alta la clínica en
  `PlatformOrganizationService`, en contexto de plataforma.
- **Capacidad clínica**: `ClinicalCapacityService.evaluate(membership, credential, fecha)` es la regla pura
  (membresía activa → sin retiro → credencial → no vencida en `fecha`, hora de Perú).
- **Nota de consulta**: `ConsultationNoteService.open(patientId, attendedAt)` exige paciente activo, Historia
  `COMPLETED`, ninguna otra nota abierta y `attendedAt` no futura ni anterior a la última sellada; ya mapea sus
  errores a RFC 9457. Una nota abierta y vacía se puede **descartar** (se borra la fila).
- En producción las migraciones corren con un dueño **no superusuario** (Neon) y la RLS forzada aplica también a
  Flyway (memoria del proyecto: deploy #89).

## Goals / Non-Goals

**Goals:**
- Que un choque de agenda sea **imposible a nivel de dato**, no solo improbable en el servicio.
- Que la hora que ve y escribe la recepción sea siempre la **de la clínica**, nunca la del navegador.
- "Atender con nota" **atómico**: o cita `ATENDIDA` + nota enlazada, o nada.

**Non-Goals:**
- Librería de calendario de terceros; recurrencia; feriados (ver proposal, Non-goals).

## Decisions

### D1 — Tabla `appointments` con inicio y fin persistidos

Columnas: `organization_id`, `patient_id` (FK compuesta a `patients`), `dentist_user_id` (FK a `users`; la
pertenencia a la clínica la valida el servicio, como en las firmas), `starts_at` y `ends_at` (`TIMESTAMPTZ`),
`status` (`CHECK` con los cuatro valores), `reason` (≤500), `cancellation_reason` (≤500), `consultation_note_id`,
`created_at/by`, `updated_at/by`, `status_changed_at/by`. `CHECK (ends_at > starts_at)`.

Coherencia de estado en la base, no solo en el servicio (una escritura futura directa no puede dejar filas
incoherentes):

- `CHECK ((status = 'AGENDADA') = (status_changed_at IS NULL AND status_changed_by IS NULL))`: al crear, los
  `status_changed_*` quedan vacíos; toda transición los llena. Reprogramar no los toca (no cambia el estado).
- `CHECK (cancellation_reason IS NULL OR status = 'CANCELADA')`.
- `CHECK (consultation_note_id IS NULL OR status = 'ATENDIDA')`.

Se persiste `ends_at` en vez de `duration_minutes` porque la restricción anti-choque (D2) necesita el rango como
expresión indexable; `starts_at + interval` no es inmutable y no sirve en una columna generada. La duración
expuesta por la API se deriva (`ends_at - starts_at`).

### D2 — Anti-choque con restricción de exclusión (`btree_gist`)

```sql
EXCLUDE USING gist (organization_id WITH =, dentist_user_id WITH =,
                    tstzrange(starts_at, ends_at, '[)') WITH &&)
  WHERE (status IN ('AGENDADA', 'ATENDIDA'))
```

El rango semiabierto `[)` hace que 10:00–10:30 y 10:30–11:00 no choquen. El servicio pre-consulta para dar un
`409` con detalle en la vía feliz, y además traduce la violación de la restricción (`SQLState 23P01`) a ese mismo
`409`: dos reservas simultáneas no pueden pasar ambas. Cancelar o marcar `NO_ASISTIO` saca la fila del `WHERE` y
libera el hueco sin borrar nada.

*Alternativas:* bloquear la membresía del odontólogo `FOR UPDATE` y consultar solapes — correcto, pero la
garantía vive solo en el código y cualquier escritura futura que olvide el bloqueo la rompe; un
`pg_advisory_xact_lock(org, dentista)` — mismo problema. Se quedan como **plan B** si Neon no permite la
extensión (ver Riesgos).

### D3 — Instantes en la API, hora de la clínica en las reglas y en la pantalla

Siguiendo `docs/backend.md` §8.2: inicio y fin viajan como `Instant` (ISO-8601 con `Z`) y se guardan
`TIMESTAMPTZ`; nada de `LocalDateTime`. Lo que es **civil** —la semana consultada (`from`/`to` como `LocalDate`,
ambos inclusive, máximo 7 días), el día de la semana, la grilla de 5 minutos y el horario de atención— se evalúa
convirtiendo el instante a la `timezone` de `organization_settings` en el servicio. Al ser instantes, no hay hora
local ambigua ni inexistente que resolver en el backend.

Pantalla: la hora que se muestra y se elige es la **de la clínica**, no la del navegador (una recepcionista con la
laptop en otra zona vería horas corridas). `GET /api/business-hours` devuelve también `timezone`, y `core` gana
`clinicLocalToInstant` / `instantToClinicLocal` (Intl, sin librería) junto a `clinicDate.ts`, que hoy fija
`CLINIC_TIMEZONE` a mano y queda alimentado por el servidor en la agenda. Una hora local que no existe en la zona
de la clínica (salto de horario de verano; no aplica a Lima) se avanza al primer instante válido; una hora local
**repetida** (el retroceso del horario de verano) se resuelve a su **primera ocurrencia**, el instante anterior
(lo mismo que `ZonedDateTime.ofLocal` sin preferencia y que `disambiguation: "compatible"`). En ambos casos el
diálogo muestra la hora resultante con su desfase antes de confirmar. El contrato no cambia: viaja el `Instant`
ya resuelto, así que el backend nunca recibe una hora ambigua. Los tests del helper usan una zona con cambio de
horario.

La grilla y la duración (10–240, múltiplo de 5) se validan en el servicio (dependen de la zona) y su espejo en el
schema Zod (`docs/coding-style.md` §7); la duración, además, con Bean Validation en el DTO.

### D4 — Capacidad del odontólogo en la fecha de la cita

`ClinicalCapacityService` gana `requireForDate(userId, LocalDate)`: lee membresía y credencial del odontólogo
en la organización activa y aplica `evaluate(..., fechaDeLaCita)`. Lectura simple, sin `FOR SHARE`: retirar la
capacidad después de agendar **no** cancela la cita (Non-goal), así que no hay carrera que proteger. El error
es uno nuevo, `DentistNotSchedulableException` (`409`, con el motivo `ClinicalCapacityState` y la fecha de
vencimiento si aplica): **no** reutiliza `ClinicalCapacityRequiredException`, que es `403` porque describe la
falta de permiso de quien llama; aquí quien llama está autorizado y el conflicto es del dato elegido. La lista de odontólogos agendables
(`GET /api/appointments/dentists`) reutiliza `forMembers` sobre las membresías activas y filtra los habilitados
hoy; la ve cualquier miembro (solo id y nombre).

### D5 — Horario de atención como JSONB en `organization_settings`

`organization_settings.business_hours JSONB NOT NULL DEFAULT '<lun-vie 09:00-18:00, sáb 09:00-13:00>'`, con la
forma `{"MONDAY": [{"opens": "09:00", "closes": "18:00"}], ..., "SUNDAY": []}`.

- El `DEFAULT` de la columna resuelve a la vez las clínicas existentes y las nuevas, sin tocar
  `PlatformOrganizationService` ni escribir con contexto de tenant desde la plataforma.
- Sin RLS, como el resto de `organization_settings`. El aislamiento se sostiene igual: el servicio lee y escribe
  **solo** la fila cuyo id es `OrganizationContext.get()` (que sale del claim `oid` del token, nunca de la
  petición: ningún DTO ni ruta lleva id de organización), y la escritura exige además `ADMIN` de esa
  organización. Un IT lo verifica con dos organizaciones (tarea 2.2). Es el mismo modelo que ya protege la marca
  y la zona horaria.
- `PUT /api/business-hours` reemplaza la semana completa (`ADMIN`, `@PreAuthorize`), valida grilla, apertura <
  cierre y tramos sin solape, y bloquea la fila `FOR UPDATE` (dos admins: el último gana, sin mezcla).

*Alternativa:* tabla `business_hours` con una fila por tramo y RLS — más normalizada, pero la siembra para cada
clínica nueva exige escribir con el contexto de esa clínica desde la plataforma, y no hay consultas que necesiten
indexar tramos.

### D6 — Estados y transiciones bajo bloqueo de fila

Toda transición y reprogramación toma la cita `FOR UPDATE` y verifica el estado **después** del bloqueo: dos
clics simultáneos ("cancelar" y "no asistió") no pueden dejar dos cambios. "Ya llegó la hora" = `starts_at <=
now()` con el `Clock` inyectado. Reprogramar recorre las mismas validaciones que crear; la exclusión de D2 no
compara la fila consigo misma porque es un `UPDATE` de la misma fila.

### D7 — "Atender con nota" en una sola transacción

`POST /api/appointments/{id}/consultation-note`: primero exige capacidad clínica vigente a quien llama con
`ClinicalCapacityService.require()` —membresía y credencial `FOR SHARE`, igual que el sellado— y no con
`current()`, que lee sin bloquear: un retiro confirmado entre la lectura y el commit dejaría abrir y enlazar la
nota sin capacidad. Con `FOR SHARE`, o termina primero "Atender con nota" o termina primero el retiro y se
rechaza (mismo contrato que el sellado en `clinical-history`). El error es el de siempre,
`ClinicalCapacityRequiredException` (`403` con el motivo; decisión del dueño del producto en la línea de
`ADR-0016`). Luego bloquea la cita, verifica `AGENDADA` con inicio llegado o
`ATENDIDA` sin nota, llama a `ConsultationNoteService.open(patientId, startsAtInstant)` **en la misma
transacción** (propagación `REQUIRED`), guarda `consultation_note_id` y pasa a `ATENDIDA`. Si `open` lanza
(Historia no completada, nota ya abierta, paciente inactivo, atención anterior a la última sellada), la
transacción entera vuelve atrás y la excepción llega con su RFC 9457 actual.

La FK es compuesta `(organization_id, consultation_note_id) → consultation_note (organization_id, id)` con
`ON DELETE SET NULL (consultation_note_id)` (PostgreSQL ≥ 15): descartar la nota vacía desengancha la cita sin
tocar `organization_id`. `UNIQUE (consultation_note_id)`: una nota no se enlaza a dos citas. La cita tiene **una
nota vigente a la vez** (una sola columna); si esa nota se descarta —solo posible vacía, así que no hay registro
clínico que perder— la columna vuelve a NULL y la cita puede enlazar otra. No se guarda historial de notas
descartadas: no son registro clínico.

La nota no gana columna ni cambia su spec: el enlace vive en la cita.

**Orden de bloqueos.** "Atender con nota": membresía → credencial (`FOR SHARE`) → cita (`FOR UPDATE`) → inserción
de la nota. Descartar (sin cambios en `clinical-history`): nota (`FOR UPDATE`) → borrado, cuyo `SET NULL` escribe
la cita enlazada. Un ciclo exigiría que "Atender con nota" espere a una nota que otro está borrando mientras
retiene la cita que ese borrado escribe. No ocurre: si la cita ya tiene nota enlazada, se rechaza al leerla bajo
su propio bloqueo y antes de tocar notas; si la nota que se borra es de otra cita, el `SET NULL` escribe esa otra
fila, no la bloqueada; y si el paciente tiene una nota abierta visible, `open` la rechaza en su pre-chequeo
antes de insertar. Se deja un IT de la carrera (tarea 3.6) para que un cambio futuro del orden no lo rompa en
silencio; si PostgreSQL detectara un interbloqueo (`40P01`), el IT falla.

### D8 — API

| Método | Ruta | Quién |
|---|---|---|
| `GET` | `/api/appointments?from&to&dentistId` | miembro |
| `GET` | `/api/appointments/{id}` | miembro |
| `POST` | `/api/appointments` | miembro |
| `PUT` | `/api/appointments/{id}/schedule` (reprogramar) | miembro |
| `POST` | `/api/appointments/{id}/cancel` · `/attended` · `/no-show` | miembro |
| `POST` | `/api/appointments/{id}/consultation-note` | miembro con capacidad clínica |
| `GET` | `/api/appointments/dentists` | miembro |
| `GET` / `PUT` | `/api/business-hours` (el `GET` incluye `timezone`) | miembro / `ADMIN` |

La respuesta de la cita trae nombres de paciente y odontólogo (evita N+1 en el front), estado, inicio y fin
(instantes), duración, motivos, `consultationNoteId` y quién/cuándo del último cambio de estado. Errores nuevos:
`AppointmentConflictException` (choque, `409`), `DentistNotSchedulableException` (`409`, D4),
`AppointmentClosedException` (`409`),
`AppointmentNotStartedException` (`409`), `OutsideBusinessHoursException` (`422`), `AppointmentInPastException`
(`422`).

### D9 — Frontend: grilla semanal propia

Módulo `modules/appointments` (componentes, hooks de React Query, schemas Zod) y screens finas. La vista semanal
es una grilla CSS propia: columnas lunes–domingo, filas de 15 minutos entre la apertura más temprana y el cierre
más tardío de la semana, horas fuera de horario sombreadas con token, bloques posicionados por inicio/duración;
citas de distintos odontólogos que se solapan se reparten el ancho de la columna. Color por odontólogo: paleta
de tokens `--chart-1..5` por índice en la lista de agendables (con más de 5 se repite, el nombre sigue en el
bloque). Bajo el breakpoint `md`, un día por vez.

*Alternativa:* FullCalendar o similar — rápido, pero pesa, su tematización pelea con los tokens por clínica
(`docs/frontend.md`) y la grilla es la pieza de portafolio del change.

Integración con secciones y rutas (`add-app-shell` D1):

- `PATHS.APPOINTMENTS = "/appointments"` y `PATHS.BUSINESS_HOURS = "/settings/business-hours"` en
  `routes/paths.ts`.
- `SectionId` suma `"appointments"` (sin roles) y `"business-hours"` (`roles: ["ADMIN"]`) en
  `core/config/sections`; el menú (`navItems`) suma "Agenda" e "Horario de atención" con su ícono lucide, este
  último junto a Marca y Personal.
- `routes/index.tsx`: `section("appointments", [{ path: PATHS.APPOINTMENTS, element: <AppointmentsScreen /> }])` y
  `section("business-hours", [...<BusinessHoursSettingsScreen />])`, screens de una línea en
  `screens/appointments/` y `screens/settings/`. El bloqueo de ruta es solo UX: el `PUT` lleva `@PreAuthorize`. "Agendar próxima cita" en la nota navega a la Agenda con `?patientId&date`, que abre el
diálogo precargado. El selector de paciente reutiliza la búsqueda de `patients`.

## Risks / Trade-offs

- **`btree_gist` en Neon con dueño no superusuario** → es extensión *trusted* desde PostgreSQL 13 (la puede crear
  quien tenga `CREATE` en la base); verificarlo en una rama de Neon **antes** del merge (tarea 1.1). Si falla, plan
  B de D2 (bloqueo de membresía + consulta) y se documenta.
  **Verificado 2026-11-24 (tarea 1.1):** en una rama de Neon, como `odontorisas_admin`, PostgreSQL 16.16 y
  `CREATE EXTENSION btree_gist` (1.7) sin error; en `postgres:16-alpine` (Testcontainers), 16.15 y 1.7. Plan B
  descartado.
- **`ON DELETE SET NULL (columna)` exige PostgreSQL ≥ 15** → confirmar la versión de Neon y de la imagen de
  Testcontainers (tarea 1.1); si no, trigger `BEFORE DELETE` en `consultation_note` que limpia el enlace.
- **Instantes en el contrato, reglas en hora de la clínica** → un consumidor futuro (app móvil, `public-booking`)
  debe convertir con la `timezone` de `GET /api/business-hours`, no con la del dispositivo: se documenta en el
  `@Schema` de OpenAPI.
- **Cambiar el horario no reubica citas** → una cita puede quedar fuera de horario; se ve igual en la agenda y la
  reprogramación la obliga a entrar. Aceptado (spec).
- **Color por índice** → el color de un odontólogo puede cambiar si cambia la lista de agendables. Aceptado en v1.

## Migration Plan

`V32__appointments.sql`: `CREATE EXTENSION IF NOT EXISTS btree_gist`, tabla `appointments` con índices
(`organization_id`; `(organization_id, starts_at)` para la consulta semanal), RLS forzada, restricción de
exclusión, FK compuestas; `ALTER TABLE organization_settings ADD COLUMN business_hours JSONB NOT NULL DEFAULT
...`. Solo datos falsos en producción, así que no hay migración de datos. Rollback: revertir el deploy; la
migración es aditiva (tabla y columna nuevas) y no rompe la versión anterior.
