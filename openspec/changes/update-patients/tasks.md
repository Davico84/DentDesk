## 1. Precondición

- [ ] 1.1 Confirmar que `update-multi-tenancy` (companion) está aplicado y archivado — sin
      `AuditorAware`/`@EnableJpaAuditing` este change no tiene efecto (design.md, riesgo).

## 2. Migración y entidad

- [ ] 2.1 `V10__patient_authorship.sql`: columnas `created_by`/`updated_by`, `NULL`-ables,
      `REFERENCES users(id) ON DELETE SET NULL` (design.md D1-D3).
- [ ] 2.2 `Patient`: campos `@CreatedBy`/`@LastModifiedBy` + `@EntityListeners(AuditingEntityListener.class)`.

## 3. Tests

- [ ] 3.1 Test (unit o slice `@DataJpaTest`) que crea un `Patient` con `IdentityContext` poblado y
      verifica `createdBy`/`updatedBy` (Scenario "Se registra la autoría al crear y editar").
- [ ] 3.2 Test que edita el mismo paciente con otro `IdentityContext` y verifica que `updatedBy`
      cambia y `createdBy` no (mismo Scenario).
- [ ] 3.3 Verificar (test o inspección) que un paciente insertado sin `IdentityContext` (simulando
      uno anterior a este change) conserva `created_by`/`updated_by` en `NULL` sin fallar
      (Scenario "Los pacientes anteriores a este mecanismo no tienen autoría inventada").

## 4. Cierre

- [ ] 4.1 `openspec validate update-patients --strict` en verde.
- [ ] 4.2 `./mvnw -B verify` en verde.
- [ ] 4.3 Confirmar que `PatientResponse` y el contrato OpenAPI **no** cambiaron (alcance decidido:
      solo persistencia).
- [ ] 4.4 Commit (`docs/commits.md`) — este change no toca frontend ni contrato.
