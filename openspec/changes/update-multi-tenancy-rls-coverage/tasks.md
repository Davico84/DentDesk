## 1. Clasificación pura y sus pruebas (scope: back, sin Docker)

- [ ] 1.1 Escribir `TenantTableClassificationTest` **primero**: una instantánea correcta (devuelve vacío) y una violación deliberada por regla (sin clasificar, entrada obsoleta, en dos listas, RLS no habilitada, RLS no forzada, sin política, lista 1 sin `organization_id`, lista 3 con RLS, lista 3 sin `organization_id`, lista 4 con `organization_id`, función `SECURITY DEFINER` sin declarar, función declarada obsoleta, función sin `search_path` fijo); cada una debe nombrar tabla o función y regla. Debe fallar antes de implementar.
- [ ] 1.2 Crear `TenantTableClassification` (clase de test, sin Spring) con `TableInfo`, `FunctionInfo`, `Violation` y `evaluate(List<TableInfo>, List<FunctionInfo>)`.
- [ ] 1.3 Declarar las cinco listas como `Map<nombre, motivo>` con el contenido de `design.md` (12 + 2 + 3 + 8 tablas + 2 funciones); la lista 3 incluye el ADR de cada entrada (`ADR-0003` ×2, `ADR-0022` para `invitations`) y la 5 los tests que cubren cada función.

## 2. Inventario contra PostgreSQL real (scope: back)

- [ ] 2.1 Crear `TenantTableInventoryIT extends AbstractRlsIntegrationTest`, conectado con `appConnection()` (rol no superusuario).
- [ ] 2.2 Extraer la instantánea con `pg_class` + `pg_attribute` + `pg_policies` (**no** `information_schema.columns`); excluir `flyway_schema_history`. Funciones con `pg_proc` + `pg_namespace` (`prosecdef`), excluyendo las de extensiones vía `pg_depend` (`deptype = 'e'`), y `proconfig` para `search_path`.
- [ ] 2.3 Guardián de importador vacío: la instantánea contiene `patients` e `invitations`; la de funciones contiene `current_user_is_org_admin` y ninguna de `btree_gist`.
- [ ] 2.4 Ejecutar `evaluate` y fallar listando **todas** las violaciones, no solo la primera.
- [ ] 2.5 Ejecutarlo; si la realidad contradice una lista (p. ej. una tabla de la lista 1 sin `FORCE`), **corregir la lista o abrir hallazgo** — no ajustar el test para que pase. Registrar lo encontrado en el ADR.

## 3. ADR (scope: docs)

- [ ] 3.1 `docs/adr/0022-cobertura-rls-y-excepciones-por-clinica.md` — ya escrito en el propose; en el apply, ajustar con lo que 2.5 revele y pasar su estado a "Implementado".
- [ ] 3.2 `docs/adr/0003-excepcion-rls-metadatos-del-tenant.md`: **solo** su línea de estado → *"Aceptado — válida con matiz, revalidada en ADR-0022"*.
- [ ] 3.3 Verificar que el número 0022 sigue libre (la rama `update-module-boundaries` reserva 0021) y que los enlaces del ADR resuelven.

## 4. Listas al día (scope: back)

- [ ] 4.1 Al empezar el apply, recalcular las listas contra las migraciones de `origin/dev` (no la rama `dev` local) y compararlas con `design.md` (recalculado con V32: 25 tablas, `appointments` en la lista 1).
- [ ] 4.2 Si aparece una tabla nueva, comprobar en su migración real `organization_id`, `@TenantId` y RLS forzada antes de declararla; no copiar las listas de este documento sin verificarlas.

## 5. Verificación contra violación real (docs/testing.md §5)

- [ ] 5.1 Agregar a mano una migración local con `CREATE TABLE zz_probe (organization_id BIGINT)` sin clasificar y comprobar que `TenantTableInventoryIT` **falla** nombrándola; revertir.
- [ ] 5.2 Quitar temporalmente `FORCE ROW LEVEL SECURITY` de una tabla de la lista 1 en una migración local y comprobar que falla; revertir.
- [ ] 5.3 Agregar a mano una función `SECURITY DEFINER` sin declarar en una migración local y comprobar que falla nombrándola; revertir.
- [ ] 5.4 Dejar en el PR la evidencia (mensajes de error) de las tres.
- [ ] 5.5 `mvn verify` verde (con Docker).

## 6. Documentación (scope: docs)

- [ ] 6.1 `docs/backend.md §8.1`: enlazar el inventario y la regla "tabla nueva ⇒ se clasifica en el mismo cambio; función `SECURITY DEFINER` nueva ⇒ se declara con su test y fija `search_path`".
- [ ] 6.2 `docs/architecture.md` (aislamiento): reemplazar la frase de "`users`/`refresh_tokens` sin RLS" por la clasificación completa y el enlace al ADR-0022.
- [ ] 6.3 `docs/testing.md`: añadir el inventario a "Test de aislamiento" y decir que garantiza **clasificación**, no corrección de las políticas (D6).
- [ ] 6.4 `docs/vision.md`: al archivar, marcar la extensión de `multi-tenancy` como construida y enlazar el change archivado.
- [ ] 6.5 `openspec validate update-multi-tenancy-rls-coverage --strict` y medir a mano que cada requirement ADDED ≤500 caracteres.
