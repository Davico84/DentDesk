## Why

`docs/backend.md §8.6` exige `created_by`/`updated_by` en toda entidad de negocio nueva, y
`patients` ya declara la historia clínica como *"el dato más sensible del producto"* con retención
legal (`openspec/specs/patients/spec.md`). `Patient` es la única entidad de negocio construida hoy
y no lleva autoría: RLS dice **qué organización** tocó una ficha, pero nada dice **qué usuario**.

Este change depende de `update-multi-tenancy` (companion, propuesto junto a este): el mecanismo
genérico (`AuditorAware<Long>`, `@EnableJpaAuditing`) nace ahí; aquí se aplica por primera vez,
igual que `patients` fue la primera prueba real del binding automático de tenant.

## What Changes

- `Patient` declara `@CreatedBy`/`@LastModifiedBy` + `@EntityListeners(AuditingEntityListener.class)`.
- Nueva migración Flyway: columnas `created_by BIGINT REFERENCES users(id)` y
  `updated_by BIGINT REFERENCES users(id)`, nulas (los pacientes existentes no tienen autoría
  conocida — no hay backfill posible).
- **Alcance quirúrgico, decidido explícitamente**: solo persistencia. `PatientResponse` **no**
  expone estos campos, y no hay cambio de UI. La garantía queda lista para cuando haga falta
  consultarla (soporte, auditoría), sin comprometerse hoy a un contrato ni a una pantalla.

## Non-goals

- **No** se expone `createdBy`/`updatedBy` en `PatientResponse` ni en el contrato OpenAPI — decisión
  explícita de alcance, no un olvido. Sería un change aparte (`update-patients` de nuevo, o parte de
  una capacidad de auditoría) si se decide mostrarlo.
- **No** se resuelve el `userId` a nombre de usuario (`fullName`) — solo se persiste el identificador.
- **No** hay backfill de pacientes existentes: nacen con `created_by`/`updated_by` en `NULL`. No hay
  forma honesta de inventar quién los creó.

## Capabilities

### New Capabilities

*(ninguna)*

### Modified Capabilities

- `patients`: nuevo requirement — toda ficha de paciente registra qué usuario la creó y cuál fue el
  último en modificarla, usando el mecanismo automático de `multi-tenancy`.

## Impact

- **Código**: `persistence.entity.Patient` (nuevos campos + listener), nueva migración Flyway
  (`V<n>__patients_authorship.sql` o el nombre que corresponda al siguiente número libre).
- **Contrato**: sin cambios — `PatientResponse` no se toca (alcance decidido).
- **Frontend**: sin cambios.
- **Dependencia dura**: requiere `update-multi-tenancy` (companion) aplicado y archivado primero —
  sin `AuditorAware`/`@EnableJpaAuditing`, `@CreatedBy`/`@LastModifiedBy` no hace nada.
