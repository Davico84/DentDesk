## Why

Hoy toda entidad de negocio bajo RLS registra **qué organización** la tocó, pero ninguna registra
**qué usuario** dentro de esa organización lo hizo. `patients` ya declara la historia clínica como
*"el dato más sensible del producto"* con retención legal (`openspec/specs/patients/spec.md`), y el
estándar recién fijado en `docs/backend.md §8.6` exige `created_by`/`updated_by` en toda entidad de
negocio nueva — pero el mecanismo que los rellena (`AuditorAware<Long>` + Spring Data JPA Auditing)
todavía no existe en ningún sitio.

Es el mismo problema, resuelto una vez, que ya resolvió esta capacidad para el aislamiento por
organización (`update-multi-tenancy` archivado 2026-07-15): un contexto por transacción
(`IdentityContext.get()`, poblado por `TenantAwareTransactionManager`) que hoy solo alimenta RLS.
Conectarlo también a la auditoría de autoría es una extensión natural del mismo mecanismo, no una
pieza nueva — y evita que cada capacidad de negocio futura tenga que decidir esto por su cuenta.

## What Changes

- Nuevo `AuditorAware<Long>` (`infra.config` o `infra.tenancy`, a decidir en design.md) que expone
  `IdentityContext.get()` a Spring Data JPA Auditing.
- `@EnableJpaAuditing` habilitado a nivel de aplicación.
- Toda entidad de negocio bajo RLS **puede** declarar `@CreatedBy`/`@LastModifiedBy` (con
  `@EntityListeners(AuditingEntityListener.class)`) y se rellenan solas — mismo principio *"por
  defecto, no por disciplina"* que ya rige el binding automático de tenant.
- Caso límite: durante el registro (`service.auth`) no hay `IdentityContext` (la identidad se crea
  en esa misma transacción) — el `AuditorAware` devuelve `Optional.empty()` ahí, no lanza. Mismo
  caso límite que `bindIdentity(...)` ya resuelve para el tenant (`docs/backend.md §8.1.1`).
- Este change **no** aplica el mecanismo a ninguna entidad de negocio todavía — eso lo hace el
  change companion `update-patients`, como primera prueba real (mismo patrón que la vez anterior:
  el mecanismo nació aquí, `patients` lo probó).

## Non-goals

- **No** se migra ninguna entidad existente (`Patient`, `Organization`, `Membership`...) a llevar
  `created_by`/`updated_by` — eso es alcance de cada capacidad que lo adopte (`update-patients` es
  la primera).
- **No** se construye un log de auditoría completo (histórico de cambios, diffs por campo). Es
  autoría del estado actual (quién creó/modificó por última vez), no un *audit log* de eventos.
- **No** se toca el mecanismo de `createdAt`/`updatedAt` (`@CreationTimestamp`/`@UpdateTimestamp` de
  Hibernate) — sigue como está; `createdBy`/`updatedBy` es un mecanismo paralelo, no un reemplazo.

## Capabilities

### New Capabilities

*(ninguna — este change es una extensión de una capacidad existente)*

### Modified Capabilities

- `multi-tenancy`: nuevo requirement — el sistema provee un mecanismo automático para que cualquier
  entidad de negocio registre el usuario que la creó/modificó por última vez, análogo al mecanismo
  ya existente de binding automático de tenant.

## Impact

- **Código**: `infra.config`/`infra.tenancy` (nuevo `AuditorAware`), `application.yml` o
  `@Configuration` (`@EnableJpaAuditing`). Sin cambios de esquema en este change (ninguna entidad
  aún declara las columnas).
- **Dependencias**: ninguna nueva — Spring Data JPA Auditing ya viene con `spring-boot-starter-data-jpa`.
- **Otras capacidades**: habilita a `update-patients` (companion) y a toda capacidad de negocio
  futura (`staff`, `appointments`...) a adoptar `createdBy`/`updatedBy` sin decidir el mecanismo de
  nuevo — igual que hoy adoptan RLS solo con declarar `@TenantId`.
