## 1. Clasificación pura y sus pruebas (scope: back, sin Docker)

- [x] 1.1 Escribir `TenantTableClassificationTest` **primero**: una instantánea correcta (devuelve vacío) y una violación deliberada por regla (sin clasificar, entrada obsoleta, en dos listas, RLS no habilitada, RLS no forzada, sin política, lista 1 sin `organization_id`, lista 3 con RLS, lista 3 sin `organization_id`, lista 4 con `organization_id`, función `SECURITY DEFINER` sin declarar, sobrecarga sin declarar, función declarada obsoleta, función sin `search_path` o con un valor no permitido, vista en `public`, objeto en otro esquema, rol que puede asumir un rol con `BYPASSRLS`; para `search_path`: `"public"` entre comillas pasa, `public, pg_temp` y ausente fallan); cada una debe nombrar el objeto y la regla. Debe fallar antes de implementar. Hecho: 23 casos; antes de existir la clase no compilaba.
- [x] 1.2 Crear `TenantTableClassification` (clase de test, sin Spring) con `TableInfo`, `FunctionInfo` (firma + valor de `search_path`), `RoleInfo`, `Violation` y `evaluate(...)`.
- [x] 1.3 Declarar las cinco listas como `Map<nombre, motivo>` con el contenido de `design.md` (12 + 2 + 3 + 8 tablas + 2 funciones); la lista 3 incluye el ADR de cada entrada (`ADR-0003` ×2, `ADR-0022` para `invitations`) y la 5 los tests que cubren cada función.

## 2. Inventario contra PostgreSQL real (scope: back)

- [x] 2.1 Crear `TenantTableInventoryIT extends AbstractRlsIntegrationTest`, conectado con `appConnection()` (rol no superusuario).
- [x] 2.2 Extraer la instantánea con `pg_class` + `pg_attribute` + `pg_policies` (**no** `information_schema.columns`); excluir `flyway_schema_history`. Funciones con `pg_proc` + `pg_namespace` (`prosecdef`, `prokind IN ('f','p')`), identificadas por `proname` + `pg_get_function_identity_arguments(oid)`, excluyendo las de extensiones vía `pg_depend` (`deptype = 'e'`), y el valor de `search_path` leído con `pg_options_to_table(proconfig)` y normalizado (comas, espacios, comillas). Vistas (`relkind IN ('v','m')`) de `public`. Objetos fuera de `public` con una **consulta aparte** sin el filtro de `public`, excluyendo `information_schema` y `nspname ~ '^pg_'`. Rol: las dos consultas de D7 (atributos y dueño de las tablas) con `pg_has_role(session_user, ..., 'SET')`.
- [x] 2.3 Guardián de importador vacío: la instantánea contiene por nombre `patients` e `invitations`; la de funciones contiene `current_user_is_org_admin` y `clinic_capacity_facts` (con `EXECUTE` revocado a `PUBLIC`) y ninguna de `btree_gist`.
- [x] 2.3b Probar las dos consultas del rol contra PostgreSQL real (Testcontainers). El superusuario del contenedor crea roles de prueba `LOGIN` (y los borra al final), y **cada caso abre su propia conexión JDBC como ese rol**, porque la consulta evalúa `session_user`; no se usa el rol de la app. Casos: `SUPERUSER` directo (falla); `BYPASSRLS` directo (falla); cadena `SET TRUE` → `SET TRUE` → rol con `BYPASSRLS` (falla); cadena con un eslabón intermedio `SET FALSE` hacia un rol con `BYPASSRLS` (**no** falla); miembro `INHERIT TRUE, SET FALSE` de un rol con `BYPASSRLS` (**no** falla); miembro `SET TRUE` del dueño de las tablas de `public` (falla); rol sin nada de lo anterior (no falla). Y un objeto en un esquema de prueba distinto de `public` (falla).
- [x] 2.4 Ejecutar `evaluate` y fallar listando **todas** las violaciones, no solo la primera.
- [x] 2.5 Ejecutarlo; si la realidad contradice una lista (p. ej. una tabla de la lista 1 sin `FORCE`), **corregir la lista o abrir hallazgo** — no ajustar el test para que pase. Registrar lo encontrado en el ADR. Hecho: el catálogo coincidió con las listas. Hallazgo: `OrganizationRlsIT`, `AutomaticTenantBindingIT` y `AutomaticAuditingIT` dejaban sus tablas de prueba en `public`; ahora las borran al terminar (registrado en ADR-0022).

## 3. ADR (scope: docs)

- [x] 3.1 `docs/adr/0022-cobertura-rls-y-excepciones-por-clinica.md` — ya escrito en el propose; en el apply, ajustar con lo que 2.5 revele y pasar su estado a "Implementado".
- [x] 3.2 `docs/adr/0003-excepcion-rls-metadatos-del-tenant.md`: **solo** su línea de estado → *"Aceptado — válida con matiz, revalidada en ADR-0022"*.
- [x] 3.3 Verificar que el número 0022 sigue libre (la rama `update-module-boundaries` reserva 0021) y que los enlaces del ADR resuelven.

## 4. Listas al día (scope: back)

- [x] 4.1 Al empezar el apply, recalcular las listas contra las migraciones de `origin/dev` (no la rama `dev` local) y compararlas con `design.md` (recalculado con V32: 25 tablas, `appointments` en la lista 1). Hecho: con V33, 25 tablas y las dos funciones de `design.md` (V33 solo reescribe una política).
- [x] 4.2 Si aparece una tabla nueva, comprobar en su **migración** `organization_id`, RLS forzada y políticas, y en su **entidad Java** `@TenantId` (el inventario solo ve el catálogo, no las anotaciones), antes de declararla; no copiar las listas de este documento sin verificarlas. Hecho: no apareció ninguna tabla nueva.

## 5. Verificación contra violación real (docs/testing.md §5)

- [x] 5.1 Agregar a mano una migración local con `CREATE TABLE zz_probe (organization_id BIGINT)` sin clasificar y comprobar que `TenantTableInventoryIT` **falla** nombrándola; revertir.
- [x] 5.2 Quitar temporalmente `FORCE ROW LEVEL SECURITY` de una tabla de la lista 1 en una migración local y comprobar que falla; revertir.
- [x] 5.3 Agregar a mano una función `SECURITY DEFINER` sin declarar en una migración local y comprobar que falla nombrándola; revertir.
- [ ] 5.4 Dejar en el PR la evidencia (mensajes de error) de las tres.
- [x] 5.5 `mvn verify` verde (con Docker). Hecho: 368 unitarios + 549 de integración, sin fallos ni saltados.

## 6. Documentación (scope: docs)

- [x] 6.0 `docs/deployment.md`: agregar la consulta de D7 como chequeo manual del rol `odontorisas_app` en Neon (sin `SUPERUSER`/`BYPASSRLS`, sin poder asumir con `SET ROLE` un rol que los tenga, p. ej. `neon_superuser`, ni a `odontorisas_admin`, dueño de las tablas).
- [x] 6.1 `docs/backend.md §8.1`: enlazar el inventario y la regla "tabla nueva ⇒ se clasifica en el mismo cambio; función `SECURITY DEFINER` nueva ⇒ se declara con su test y fija `search_path`".
- [x] 6.2 `docs/architecture.md` (aislamiento): reemplazar la frase de "`users`/`refresh_tokens` sin RLS" por la clasificación completa y el enlace al ADR-0022.
- [x] 6.3 `docs/testing.md`: añadir el inventario a "Test de aislamiento" y decir que garantiza **clasificación**, no corrección de las políticas (D6).
- [ ] 6.4 `docs/vision.md`: al archivar, marcar la extensión de `multi-tenancy` como construida y enlazar el change archivado.
- [x] 6.5 `openspec validate update-multi-tenancy-rls-coverage --strict` y medir a mano que cada requirement ADDED ≤500 caracteres.
