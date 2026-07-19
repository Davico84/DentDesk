## 1. Mecanismo automático de autoría

- [ ] 1.1 `infra.tenancy.IdentityAuditorAware` implementando `AuditorAware<Long>`, delegando en
      `common.IdentityContext.get()`; `Optional.empty()` cuando no hay contexto (D3).
- [ ] 1.2 `infra.config.JpaAuditingConfig` con `@EnableJpaAuditing(auditorAwareRef = "identityAuditorAware")` (D2).

## 2. Enforcement (ArchUnit)

- [ ] 2.1 Nueva regla: toda `@Entity` con `@TenantId` declara `@EntityListeners(AuditingEntityListener.class)`
      y los campos `@CreatedBy`/`@LastModifiedBy` (D5). Vive junto a `MultiTenancyArchitectureTest`.
- [ ] 2.2 Test que prueba que la regla **falla de verdad** ante una violación (mismo criterio que el
      candado de `TenantSession`: no basta con que la regla exista, hay que demostrar que corta).

## 3. Verificación end-to-end

- [ ] 3.1 Entidad de prueba (test-only) con `@EntityListeners(AuditingEntityListener.class)` +
      `@CreatedBy`/`@LastModifiedBy`, en `tenancy` (test sources) — distinta de `TestBusinessAccess`
      porque esta necesita ciclo de vida JPA real, no consulta nativa (D4).
- [ ] 3.2 `AutomaticAuditingIT`: crear con `IdentityContext` poblado → `createdBy`/`updatedBy` quedan
      con ese `userId` (Scenario "Se registra quién creó y quién modificó").
- [ ] 3.3 `AutomaticAuditingIT`: actualizar con otro `IdentityContext` → `updatedBy` cambia,
      `createdBy` no se altera (mismo Scenario).
- [ ] 3.4 `AutomaticAuditingIT`: guardar sin `IdentityContext` (como en el registro) → no lanza,
      autoría queda `NULL` (Scenario "Los flujos previos a la sesión siguen funcionando").

## 4. Cierre

- [ ] 4.1 `openspec validate update-multi-tenancy --strict` en verde.
- [ ] 4.2 `./mvnw -B verify` en verde.
- [ ] 4.3 Commit(s) por scope (`docs/commits.md`) — este change no toca frontend ni contrato.
