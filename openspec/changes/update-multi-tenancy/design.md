## Context

`docs/backend.md §8.6` fija el estándar (`created_by`/`updated_by` en toda entidad de negocio
nueva) pero no existe el mecanismo que los rellena. El precedente directo es el propio
`update-multi-tenancy` archivado (2026-07-15): un contexto por transacción
(`IdentityContext`/`OrganizationContext`, poblado por `JwtAuthenticationFilter` y leído por
`TenantAwareTransactionManager`) que hoy solo alimenta RLS. Este change conecta ese mismo contexto
a Spring Data JPA Auditing.

Verificado en el código actual: `IdentityContext.get()` devuelve `Long` (`common/IdentityContext.java`);
`TenantAwareTransactionManager` ya lo puebla en `doBegin` antes de que arranque cualquier operación
de negocio; ninguna entidad usa hoy `@CreatedBy`/`@LastModifiedBy` ni `@EnableJpaAuditing` — el
mecanismo de timestamps existente (`@CreationTimestamp`/`@UpdateTimestamp`) es de Hibernate, no de
Spring Data, y no sabe *quién*.

## Goals / Non-Goals

**Goals:**
- Un `AuditorAware<Long>` que expone `IdentityContext.get()` a Spring Data JPA Auditing.
- Que declarar `@CreatedBy`/`@LastModifiedBy` en una entidad baste para que se rellenen solos — sin
  que el service escriba una línea (mismo principio que el binding de tenant).
- Verificar el mecanismo end-to-end con una entidad de prueba (no una tabla de negocio real —esa
  es responsabilidad de `update-patients`), igual que `AutomaticTenantBindingIT` lo hizo para RLS.

**Non-Goals:**
- Migrar `Patient` ni ninguna entidad existente (alcance del change companion `update-patients`).
- Un *audit log* de eventos/histórico de cambios — esto es autoría del estado actual, no un log.
- Tocar `@CreationTimestamp`/`@UpdateTimestamp` — siguen como están.

## Decisions

### D1 — Dónde vive el `AuditorAware`

`infra.tenancy.IdentityAuditorAware`, junto a `TenantAwareTransactionManager` e `IdentityContext`:
son la misma familia (leen el mismo contexto por transacción), y ya es el paquete que agrupa el
enganche automático. Alternativa descartada: `infra.config` — se reserva para *habilitar* el
mecanismo (`@EnableJpaAuditing`), no para la lógica que lee el contexto.

### D2 — Habilitación separada de la lógica

`@EnableJpaAuditing(auditorAwareRef = "identityAuditorAware")` vive en una clase de configuración
propia y pequeña en `infra.config` (`JpaAuditingConfig`), no como anotación sobre
`IdentityAuditorAware` ni sobre la clase principal de Spring Boot. Mantiene el mismo patrón que
`TenantAwareTransactionManager` (`@Component("transactionManager")` simple, sin anotaciones de
"encendido" mezcladas) y hace explícito, en un solo sitio, que el mecanismo está activo.

### D3 — Caso sin contexto: `Optional.empty()`, no excepción

Durante el registro (`service.auth`), la identidad se crea en la misma transacción en la que se
usa — no hay `IdentityContext` todavía. `getCurrentAuditor()` debe devolver `Optional.empty()` en
ese caso (Spring Data Auditing lo tolera: deja `created_by`/`updated_by` en `NULL`), **no lanzar**.
Es el mismo caso límite que `TenantAwareTransactionManager` ya resuelve para el tenant
(`docs/backend.md §8.1.1`, *"en el bootstrap no hay nada que propagar"*) — aquí es idéntico, no una
excepción nueva que inventar.

> **Nota sobre `docs/backend.md §8.5`** ("no uses `Optional` en el retorno salvo finders de un único
> resultado"): `AuditorAware<Long>.getCurrentAuditor(): Optional<Long>` es la firma que **impone la
> interfaz de Spring Data**, no una elección propia — la regla del proyecto aplica a las APIs que
> diseñamos nosotros, no a implementar un contrato externo.

### D4 — Verificación con entidad de prueba, no con `TestBusinessAccess`

`TestBusinessAccess` (el fixture de `update-multi-tenancy` original) usa **consulta nativa** a
propósito — así prueba que RLS actúa sin ayuda de la capa de aplicación. Pero
`@CreatedBy`/`@LastModifiedBy` **solo se disparan por el ciclo de vida de una entidad JPA**
(`persist`/`merge` a través del `EntityManager` u otro repositorio), nunca por SQL nativo. Por eso
hace falta un fixture **distinto**: una `@Entity` de prueba con
`@EntityListeners(AuditingEntityListener.class)` + `@CreatedBy`/`@LastModifiedBy`, en el mismo
paquete de test (`tenancy`), guardada vía un repositorio Spring Data normal. El nuevo test
(`AutomaticAuditingIT`) cubre: (a) `createdBy`/`updatedBy` se rellenan con el `userId` del contexto,
(b) al actualizar, `updatedBy` cambia y `createdBy` no, (c) sin contexto (`IdentityContext` vacío,
como en el registro), el guardado **no lanza** y deja las columnas en `NULL`.

### D5 — Enforcement: ArchUnit, mismo patrón que el candado de `TenantSession`

Regla nueva de arquitectura: **toda `@Entity` con `@TenantId`** (es decir, toda entidad de negocio
bajo RLS) **debe** declarar `@EntityListeners(AuditingEntityListener.class)` y los campos
`@CreatedBy`/`@LastModifiedBy`. Mismo criterio que ya aplica el proyecto (ESLint para "un componente
por archivo", ArchUnit para el candado de `TenantSession`): una regla del estándar sin candado
mecánico se olvida tarde o temprano — y aquí el olvido no es ruidoso, dos columnas se quedan en
`NULL` en silencio. Vive junto a `MultiTenancyArchitectureTest` (mismo test class o uno hermano).

## Risks / Trade-offs

- **[Riesgo] `@CreatedBy`/`@LastModifiedBy` sin `@EntityListeners(AuditingEntityListener.class)` no
  hace nada — y no falla, solo deja `NULL` en silencio.** → Mitigado por D5 (ArchUnit).
- **[Riesgo] Confundir esto con un *audit log*** (quién hizo *cada* cambio, no solo el último) y que
  alguien lo de por sentado para un caso de uso de trazabilidad completa. → Cubierto explícitamente
  en "Non-Goals"; si aparece esa necesidad real, es una capacidad/mecanismo aparte.
- **[Trade-off] `updated_by` se pisa con cada `UPDATE`**, incluida una operación técnica hecha "en
  nombre" de un proceso sin usuario real (si algún día hay jobs de sistema). No aplica hoy (no hay
  `@Async`/`@Scheduled` cableados, `docs/backend.md §8.1`), pero queda anotado para cuando aparezcan.

## Migration Plan

No hay migración de datos: este change no toca ninguna tabla existente. `update-patients` (el
companion) lleva su propia migración Flyway para añadir `created_by`/`updated_by` a `patients`.
Sin *rollback* especial: si el mecanismo se revierte, basta con quitar `@EnableJpaAuditing` — las
entidades que no lo usen no se ven afectadas.

## Open Questions

Ninguna — el diseño se apoya en la misma decisión ya tomada y probada por
`update-multi-tenancy` (2026-07-15); no hay alternativa arquitectónica real que valga la pena
enumerar aquí.
