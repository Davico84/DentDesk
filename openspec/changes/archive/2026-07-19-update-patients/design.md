## Context

`update-multi-tenancy` (companion) construye el mecanismo genérico (`AuditorAware<Long>`,
`@EnableJpaAuditing`, la regla ArchUnit que exige `@CreatedBy`/`@LastModifiedBy` en toda entidad con
`@TenantId`). Este change es la primera aplicación real, sobre `Patient` — la única entidad de
negocio construida hoy.

Decisión de alcance ya tomada explícitamente (ver proposal.md "Non-goals"): **solo persistencia**.
No se expone en `PatientResponse` ni se toca el frontend.

## Goals / Non-Goals

**Goals:**
- `Patient` cumple `docs/backend.md §8.6` y pasa la nueva regla ArchUnit de `update-multi-tenancy`.
- Los pacientes existentes (creados antes de este change) no rompen ni quedan en un estado inválido.

**Non-Goals:**
- Exponer la autoría en la API o la UI (ver proposal.md).
- Backfill de `created_by`/`updated_by` para pacientes existentes.

## Decisions

### D1 — Migración `V10__patient_authorship.sql`

Siguiente número libre tras `V9__patients.sql`. Añade dos columnas a `patients`:

```sql
ALTER TABLE patients
    ADD COLUMN created_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN updated_by BIGINT REFERENCES users (id) ON DELETE SET NULL;
```

### D2 — Columnas `NULL`-ables, no `NOT NULL`

Los pacientes ya existentes (creados en el change `add-patients`, antes de que este mecanismo
existiera) no tienen autoría real que backfillear — inventarla sería peor que no tenerla. `NOT NULL`
obligaría a un valor inventado o a bloquear la migración. Los pacientes **nuevos** sí la tendrán
siempre (el mecanismo automático la rellena en cada `INSERT`); los antiguos quedan con `NULL`
permanentemente, y es la respuesta correcta, no un estado transitorio a corregir.

### D3 — `ON DELETE SET NULL`, no `CASCADE`

Las FK existentes hacia `users` (`refresh_tokens`, `memberships`) usan `ON DELETE CASCADE` porque
esas filas **pertenecen** al usuario — tiene sentido que desaparezcan con él. Aquí es distinto: el
`Patient` no pertenece al usuario que lo creó, el usuario es solo su **autor**. Si algún día un
usuario se elimina (hoy no hay ninguna capacidad que lo haga), un `CASCADE` aquí **borraría fichas de
pacientes** — exactamente lo que el propio spec de `patients` prohíbe (*"dar de baja NO borra, es un
archivado"*). `SET NULL` preserva el paciente y solo pierde el dato de quién lo creó.

### D4 — Este change activa la regla ArchUnit del companion (no al revés)

`update-multi-tenancy` diseña la regla (su design.md D5: toda `@Entity` con `@TenantId` debe llevar
`@CreatedBy`/`@LastModifiedBy`) pero **no la activa** — `Patient` es hoy la única entidad con
`@TenantId` y no la cumpliría todavía, así que activarla allí rompería ese mismo `verify`. Aquí,
tras el paso 2 (`Patient` ya declara los campos), se activa la regla y se prueba que **falla de
verdad** ante una violación:

- Método concreto (evita el error de probarla contra código en test-sources, que el importer con
  `DO_NOT_INCLUDE_TESTS` no ve): una clase fixture **en un paquete de producción de prueba**
  (mismo patrón que se usó para probar el candado de `TenantSession`) con `@TenantId` pero sin
  `@CreatedBy`/`@LastModifiedBy`; el test confirma que ArchUnit la marca en violación; se retira
  después de confirmarlo — **no queda commiteada**, solo demuestra el corte una vez.

## Risks / Trade-offs

- **[Riesgo] Sin `update-multi-tenancy` aplicado y archivado primero, `@CreatedBy`/`@LastModifiedBy`
  en `Patient` no hace nada** (no hay `AuditorAware` registrado) y las columnas nuevas quedan
  siempre en `NULL`, silenciosamente. → Mitigado por la dependencia dura declarada en proposal.md
  ("Impact") y por la regla ArchUnit del companion, que ya vigilaría esto para la *siguiente*
  entidad — aquí toca verificarlo a mano una vez, porque `Patient` es la primera.
- **[Trade-off] Pacientes antiguos sin autoría para siempre.** Aceptado (D2): es preferible a un
  dato inventado.

## Migration Plan

1. Aplicar y archivar `update-multi-tenancy` primero (dependencia dura).
2. `V10__patient_authorship.sql` (D1).
3. `Patient` declara los campos + `@EntityListeners(AuditingEntityListener.class)`.
4. Sin *rollback* especial: revertir es una migración `DROP COLUMN`, sin pérdida de datos de
   negocio (las columnas son puramente aditivas).

## Open Questions

Ninguna.
