## 1. Precondición

- [ ] 1.1 Confirmar que `update-multi-tenancy` (companion) está **aplicado** — el `AuditorAware`/
      `@EnableJpaAuditing` tiene que existir antes de que `@CreatedBy`/`@LastModifiedBy` en
      `Patient` sirva de algo. No hace falta que esté archivado todavía (design.md, riesgo).

## 2. Migración y entidad

- [ ] 2.1 `V10__patient_authorship.sql`: columnas `created_by`/`updated_by`, `NULL`-ables,
      `REFERENCES users(id) ON DELETE SET NULL` (design.md D1-D3).
- [ ] 2.2 `Patient`: campos `@CreatedBy`/`@LastModifiedBy` + `@EntityListeners(AuditingEntityListener.class)`.

## 3. Enforcement (ArchUnit) — se activa aquí, no en el companion

- [ ] 3.1 Nueva regla: toda `@Entity` con `@TenantId` declara `@EntityListeners(AuditingEntityListener.class)`
      y los campos `@CreatedBy`/`@LastModifiedBy` (design.md D4, y el D5 del companion). Vive junto
      a `MultiTenancyArchitectureTest`.
- [ ] 3.2 Probar que la regla **falla de verdad**: una clase fixture con `@TenantId` sin
      `@CreatedBy`/`@LastModifiedBy` en un paquete de producción de prueba (no en test-sources —
      el importer con `DO_NOT_INCLUDE_TESTS` no lo vería); confirmar que ArchUnit la marca en
      violación; retirar el fixture después de confirmarlo (no queda commiteado).

## 4. Tests

- [ ] 4.1 Test (unit o slice `@DataJpaTest`) que crea un `Patient` con `IdentityContext` poblado y
      verifica `createdBy`/`updatedBy` (Scenario "Se registra la autoría al crear y editar").
- [ ] 4.2 Test que edita el mismo paciente con otro `IdentityContext` y verifica que `updatedBy`
      cambia y `createdBy` no (mismo Scenario).
- [ ] 4.3 Test que inserta una fila por SQL directo (sin pasar por el ciclo de vida JPA, simulando
      un paciente anterior a este mecanismo) y verifica que `created_by`/`updated_by` quedan `NULL`
      sin que nada falle (Scenario "Los pacientes anteriores a este mecanismo no tienen autoría
      inventada").

## 5. Cierre

- [ ] 5.1 `openspec validate update-patients --strict` en verde.
- [ ] 5.2 `./mvnw -B verify` en verde.
- [ ] 5.3 Confirmar que `PatientResponse` y el contrato OpenAPI **no** cambiaron (alcance decidido:
      solo persistencia).
- [ ] 5.4 Commit (`docs/commits.md`) — este change no toca frontend ni contrato.
