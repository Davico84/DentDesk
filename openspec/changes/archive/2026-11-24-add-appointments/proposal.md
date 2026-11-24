## Why

Sin agenda la clínica no opera en Dentdesk: hoy se registra al paciente, su historia y su odontograma, pero la cita
—lo primero que hace la recepción cada día— se lleva fuera del sistema. Es el paso 2 del corte MVP para vender
(`docs/vision.md` §2.1) y solo depende de `patients` (`ADR-0011`: rama paralela a `clinical-history`).

## What Changes

- Nueva capacidad **`appointments`** (fila 5 de `docs/vision.md`): agendamiento **interno** por el personal de la
  clínica (cualquier miembro, `ADMIN` o `MEMBER`: la cita es dato administrativo, `ADR-0016`).
- **Agendar** una cita: paciente activo, **odontólogo obligatorio con capacidad clínica vigente**, **inicio +
  duración** (múltiplos de 5 minutos), motivo opcional. Nace `AGENDADA`.
- **Sin choques**: el mismo odontólogo no puede tener dos citas vigentes que se pisen; lo garantiza también la
  base de datos (dos reservas simultáneas del mismo hueco: una gana, la otra recibe `409`).
- **Horario de atención** de la clínica, por día de la semana y con uno o más tramos (p. ej. turno partido):
  toda cita cae completa dentro de un tramo. Lo configura el `ADMIN`; las clínicas parten de un horario por
  defecto editable.
- **Estados** `AGENDADA` → `ATENDIDA` | `NO_ASISTIO` (cuando llegó la hora) | `CANCELADA` (con motivo opcional).
  Los tres finales no cambian más.
- **Reprogramar** una cita `AGENDADA`: nueva fecha, hora, duración u odontólogo, con las mismas validaciones. Es
  una acción, no un estado.
- **Enlace con la nota de consulta**: desde una cita que llegó a su hora, **"Atender con nota"** abre la nota de
  consulta del paciente y la deja enlazada a la cita, que pasa a `ATENDIDA`, todo en una transacción. Abre un
  registro clínico, así que **exige capacidad clínica vigente** a quien lo hace (en la línea de `ADR-0016`); la
  recepción sin credencial usa "marcar atendida". Y en
  sentido inverso, una nota con **próxima cita** propuesta ofrece **agendarla** con paciente y fecha precargados.
- **Vista semanal** de la agenda: columnas por día, bloques por cita con color por odontólogo, filtro por
  odontólogo, las horas fuera del horario sombreadas; clic en un hueco libre para agendar, clic en una cita para
  ver el detalle y sus acciones. En pantallas angostas se ve un día por vez.
- API nueva bajo `/api/appointments` y `/api/business-hours`; errores en RFC 9457.

## Capabilities

### New Capabilities
- `appointments`: la agenda interna de la clínica: citas con paciente, odontólogo, inicio y duración; su ciclo
  de estados y la reprogramación; el horario de atención que las acota; el enlace con la nota de consulta; y la
  vista semanal.

### Modified Capabilities
<!-- Ninguna. "Atender con nota" usa la apertura de nota de clinical-history tal cual (mismas precondiciones y
     errores); el atajo "agendar la próxima cita" es comportamiento de la agenda, especificado en appointments. -->

## Non-goals

- **Recordatorios** y avisos al paciente (correo, WhatsApp): post-MVP, con `notifications`.
- **Reserva online** por el paciente: es `public-booking`, otra capacidad y otro actor (`docs/vision.md` §2).
- **Choques del paciente** (el mismo paciente en dos sillones a la vez) y de **sillón/consultorio**: v1 solo
  impide los del odontólogo.
- **Historial de reprogramaciones** y **deshacer** un estado final (marcar por error "no asistió"): la cita
  reprogramada guarda solo su versión vigente.
- **Citas recurrentes**, lista de espera, feriados y excepciones puntuales al horario.
- **Efectos en cascada**: dar de baja al paciente o retirar la capacidad del odontólogo no cancela sus citas
  futuras; se ven en la agenda y la recepción decide.
- **Pestaña de citas en la ficha del paciente** y métricas de ocupación (`dashboard`).
- Cobro de la cita: `patient-billing`.

## Impact

- **Backend**: entidad, repositorio, servicio y controlador de citas y de horario de atención; reutiliza
  `ClinicalCapacityService` (capacidad del odontólogo en la fecha de la cita) y `ConsultationNoteService.open`.
  Migración nueva: tabla `appointments` con RLS por organización (`docs/backend.md` §8.1) y restricción de
  exclusión anti-choque (`btree_gist`); columna JSONB `business_hours` en `organization_settings` (metadato del
  tenant, sin RLS, como la zona horaria; ver design.md D5) con el horario por defecto para clínicas existentes y
  nuevas. Inicio y fin viajan como instantes UTC (`docs/backend.md` §8.2).
- **Contrato**: `contracts/openapi.json` y cliente orval regenerados. La respuesta del horario expone la zona
  horaria de la clínica, que el frontend usa para mostrar y elegir horas.
- **Frontend**: módulo `appointments` (vista semanal, diálogos de agendar/reprogramar/cancelar, detalle con
  acciones), pantalla de horario de atención para el `ADMIN`, sección "Agenda" en el menú
  (`core/config/sections`), y el botón "Agendar próxima cita" en la nota de consulta. Estándares en
  `docs/frontend.md`.
- **Operación**: `CREATE EXTENSION btree_gist` en Neon con el dueño no superusuario (es extensión *trusted*;
  verificar antes del deploy, ver design.md).
- **Docs**: `docs/domain.md` (el bosquejo de Appointment queda marcado como reemplazado por este change desde el
  propose; el modelo final entra al archivar), `docs/vision.md` (estado de `appointments`).
