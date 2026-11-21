## 1. Base de datos

- [ ] 1.1 Verificar en una rama de Neon, con el rol dueño de las migraciones, que `CREATE EXTENSION btree_gist` funciona y que la versión es PostgreSQL ≥ 15; ídem en la imagen de Testcontainers. Anotar el resultado en design.md (si falla, aplicar el plan B de D2/Riesgos antes de seguir)
- [ ] 1.2 `V32__appointments.sql`: extensión, tabla `appointments` (D1) con FK compuestas a `patients` y `consultation_note` (`ON DELETE SET NULL (consultation_note_id)`), `UNIQUE (consultation_note_id)`, índices, RLS forzada `organization_isolation` y restricción de exclusión (D2); columna `organization_settings.business_hours` con el horario por defecto (D5). Verifica: `mvn verify` levanta Flyway sin errores
- [ ] 1.3 IT de base (Postgres real): la exclusión rechaza dos citas solapadas del mismo odontólogo `AGENDADA`/`ATENDIDA`, acepta contiguas, acepta tras cancelar; RLS oculta citas de otra clínica; descartar la nota pone `consultation_note_id` en NULL sin tocar `organization_id`; los `CHECK` de coherencia de D1 rechazan con SQL crudo una `CANCELADA`-motivo en otro estado, una nota en una cita no `ATENDIDA` y `status_changed_*` vacíos fuera de `AGENDADA`

## 2. Backend: horario de atención

- [ ] 2.1 Modelo del horario (record por día con tramos), conversión JSONB en `OrganizationSettings` y `BusinessHoursService` (leer, reemplazar con `FOR UPDATE`, validar grilla de 5 min, apertura < cierre, sin solapes; `contains(LocalDateTime civilStart, LocalDateTime civilEnd)`, que solo recibe valores derivados de `Instant.atZone(zonaDeLaClínica)` dentro del servicio, nunca persistidos ni de la API; documentarlo en el Javadoc, `docs/backend.md` §8.2). Tests unitarios de la validación y de `contains` (turno partido, día cerrado, borde exacto de cierre)
- [ ] 2.2 `GET`/`PUT /api/business-hours` (`PUT` con `@PreAuthorize` `ADMIN`), DTOs con Bean Validation. Tests webmvc/IT de los scenarios "Horario de atención configurable por el administrador" (turno partido, tramos inválidos sin guardar nada, `MEMBER` → 403, horario por defecto de una organización nueva) e IT con dos organizaciones: el `ADMIN` de A lee y guarda solo el horario de A y el de B no cambia; el `GET` devuelve la `timezone` de la clínica

## 3. Backend: citas

- [ ] 3.1 Entidad `Appointment`, enum `AppointmentStatus`, repositorio (rango semanal con filtro por odontólogo, `findForUpdate`, pre-chequeo de solape excluyendo un id)
- [ ] 3.2 `ClinicalCapacityService.requireForDate(userId, fecha)` y listado de odontólogos agendables (D4), con `DentistNotSchedulableException` (`409`, motivo y vencimiento) mapeada en el handler. Tests unitarios: sin credencial, retirada, membresía inactiva, vence antes de la fecha de la cita, vigente
- [ ] 3.3 `AppointmentService.schedule` (D3): paciente activo, inicio futuro en hora de la clínica, grilla, capacidad en la fecha, horario, choque (pre-chequeo + traducción de `23P01` a `409`). Tests de "Agendar una cita", "Odontólogo con capacidad clínica", "Sin choques" y "Cita dentro del horario de atención", incluyendo una clínica en zona distinta a la de la JVM: "inicio futuro", grilla, día de la semana y horario se evalúan en la zona de la clínica
- [ ] 3.4 Reprogramar con bloqueo de fila (D6): mismas validaciones excluyendo la propia cita; estado final → `409`. Tests de "Reprogramar una cita agendada"
- [ ] 3.5 Transiciones cancelar / atendida / no asistió con bloqueo de fila, motivo de cancelación, quién y cuándo (D6). Tests de "Ciclo de estados de la cita" (incluye antes de la hora y estado final)
- [ ] 3.6 "Atender con nota" en una transacción (D7) reutilizando `ConsultationNoteService.open`, con `ClinicalCapacityService.require()` primero y el orden de bloqueos de D7. IT: éxito enlaza y deja `ATENDIDA`; quien llama sin capacidad clínica (sin credencial, retirada, vencida) → `403` sin cambios; Historia no completada, nota ya abierta, cita ya enlazada y antes de hora → `409` sin cambios ni nota creada; nota descartada deja la cita sin nota y se puede volver a atender. IT de concurrencia (pool propio): retiro de capacidad simultáneo con "Atender con nota" → uno de los dos resultados del scenario, nunca nota enlazada con capacidad ya retirada; descarte de nota simultáneo con "Atender con nota" sobre el mismo paciente → sin interbloqueo (`40P01`) y estado final coherente
- [ ] 3.7 `AppointmentController` (D8), DTOs con `Instant` y `@Schema` (`docs/backend.md` §8.2), errores RFC 9457 nuevos en el handler. Tests webmvc/IT de seguridad: `401` sin sesión, con token sin organización activa y con sesión de plataforma; membresía inactiva rechazada igual que en `patients`; `404` cita de otra clínica; agendar/reprogramar con paciente de otra clínica → `404` y con odontólogo que no es miembro → `409`, sin escribir nada; `400` rango > 7 días o invertido, `400` sin odontólogo o duración inválida
- [ ] 3.8 IT de concurrencia: dos reservas simultáneas del mismo hueco con un pool propio (no `supplyAsync` sin executor) → exactamente una creada y un `409`
- [ ] 3.9 Regenerar `contracts/openapi.json`. Verifica: `mvn verify` en verde

## 4. Frontend: agenda

- [ ] 4.1 Regenerar el cliente orval; `PATHS.APPOINTMENTS`, sección `appointments` en `core/config/sections`, ítem "Agenda" en el menú y ruta en `routes/index.tsx` con screen de una línea (design.md D9, `docs/frontend.md` §1). Verifica: el ítem aparece para `ADMIN` y `MEMBER` y marca `aria-current` en `/appointments`
- [ ] 4.1b Helpers `clinicLocalToInstant` / `instantToClinicLocal` en `core/utils` junto a `clinicDate.ts`, con la zona del servidor (design.md D3). Tests: Lima, una zona con horario de verano (hora inexistente avanzada al primer instante válido, hora repetida resuelta a su primera ocurrencia, desfase visible en el diálogo), y navegador simulado en otra zona
- [ ] 4.2 Hooks de React Query (semana, odontólogos, horario, mutaciones con invalidación de la semana) y schemas Zod con paridad del DTO (grilla de 5 min, duración 10–240, motivo ≤ 500). Tests del schema
- [ ] 4.3 Grilla semanal (D9): navegación entre semanas, horas fuera de horario sombreadas, bloques por cita con paciente, odontólogo, color y estado, solapes repartidos, filtro por odontólogo; un día por vez bajo `md`. Tests Vitest/MSW de "Vista semanal de la agenda" (ver semana, filtrar, celular)
- [ ] 4.4 Diálogo de agendar (paciente con búsqueda, odontólogo agendable, fecha, hora, duración, motivo) abierto desde un hueco libre o desde `?patientId&date`; muestra los errores `409`/`422` del backend. Tests: hueco precarga día y hora; error de choque visible
- [ ] 4.5 Detalle de la cita con solo las acciones permitidas por estado y hora (reprogramar, cancelar con motivo, atendida, no asistió, atender con nota, ir a la nota). Tests de visibilidad de acciones ("Atender con nota" oculta sin capacidad clínica, "marcar atendida" visible) y de "Atender con nota" que navega a la nota
- [ ] 4.6 "Agendar próxima cita" en la nota de consulta cuando tiene próxima cita (navega a la Agenda con paciente y fecha). Tests: aparece solo con próxima cita; precarga correcta

## 5. Frontend: horario de atención

- [ ] 5.1 `PATHS.BUSINESS_HOURS`, sección `business-hours` (`roles: ["ADMIN"]`), ítem "Horario de atención" junto a Marca y Personal, ruta con screen de una línea en `screens/settings/` (design.md D9): editor por día con tramos (agregar, quitar, día cerrado), schema Zod con las mismas reglas del backend. Tests: turno partido válido, solape y apertura ≥ cierre bloqueados en el formulario, `MEMBER` no ve la sección

## 6. Cierre

- [ ] 6.1 `pnpm validate` y `mvn verify` en verde; `openspec validate add-appointments --strict`
- [ ] 6.2 Prueba manual con datos falsos en local: agendar, choque, fuera de horario, reprogramar, cancelar, no asistió, atender con nota, próxima cita desde la nota, editar horario, vista en celular
- [ ] 6.3 `docs/domain.md`: reemplazar el bosquejo de `Appointment` por el modelo real (estados, horario, enlace con la nota)

## Workflow follow-up

- Revisión de Codex del proposal/spec (prompt en el chat) y PR del propose a `dev`.
- Al archivar: sincronizar la spec, marcar `appointments` como construida en `docs/vision.md` con su `spec.md`.
