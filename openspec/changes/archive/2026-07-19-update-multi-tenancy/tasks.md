## 1. Mecanismo automático de autoría

- [x] 1.1 `infra.tenancy.IdentityAuditorAware` implementando `AuditorAware<Long>`, delegando en
      `common.IdentityContext.get()`; `Optional.empty()` cuando no hay contexto (D3).
- [x] 1.2 `infra.config.JpaAuditingConfig` con `@EnableJpaAuditing(auditorAwareRef = "identityAuditorAware")` (D2).

## 2. Verificación end-to-end

- [x] 2.1 Entidad de prueba (test-only) con `@EntityListeners(AuditingEntityListener.class)` +
      `@CreatedBy`/`@LastModifiedBy`, en `tenancy` (test sources) — distinta de `TestBusinessAccess`
      porque esta necesita ciclo de vida JPA real, no consulta nativa (D4). Su tabla **no existe en
      Flyway** (`ddl-auto=none`): crearla por `ownerConnection()` en `@BeforeEach`, igual que
      `AutomaticTenantBindingIT` crea `test_business`.
- [x] 2.2 `AutomaticAuditingIT`: crear con `IdentityContext` poblado → `createdBy`/`updatedBy` quedan
      con ese `userId` (Scenario "Se registra quién creó y quién modificó").
- [x] 2.3 `AutomaticAuditingIT`: actualizar con otro `IdentityContext` → `updatedBy` cambia,
      `createdBy` no se altera (mismo Scenario).
- [x] 2.4 `AutomaticAuditingIT`: guardar sin `IdentityContext` (como en el registro) → no lanza,
      autoría queda `NULL` (Scenario "Los flujos previos a la sesión siguen funcionando").

## 3. Cierre

- [x] 3.1 `openspec validate update-multi-tenancy --strict` en verde.
- [x] 3.2 `./mvnw -B verify` en verde (unit tests verdes; `AutomaticAuditingIT` y el resto de ITs
      Testcontainers se saltan limpios sin Docker en este entorno — mismo comportamiento que ya
      tenía el resto de la suite, no es nuevo de este change).
- [x] 3.3 Commit(s) por scope (`docs/commits.md`) — este change no toca frontend ni contrato.

> **Nota (D5):** la regla ArchUnit que exige autoría en toda `@Entity` con `@TenantId` **no se
> activa en este change**: `Patient` aún no cumple y rompería esta misma build. La activa el
> companion `update-patients` (sus tasks 3.x), en cuanto la única entidad existente queda conforme.
