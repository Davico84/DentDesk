## Context

Auditoría de las migraciones de `dev` (V1–V31) y del código (2026-11-24). La clasificación **observada**, no supuesta:

| Lista | Tablas | Observación |
|---|---|---|
| 1. Por organización (RLS forzada + política) | `patients`, `historia_clinica`, `clinical_history_diagnosis`, `clinical_background_entry`, `consultation_note`, `consultation_note_correction`, `consultation_note_diagnosis`, `consultation_note_finding`, `dental_chart_block`, `dental_chart_finding`, `dental_chart_finding_tooth` (**11**) | todas con `organization_id`, `@TenantId` y `FORCE ROW LEVEL SECURITY` en sus migraciones |
| 2. Por identidad (RLS forzada + política) | `memberships` (tiene `organization_id` pero se aísla por `user_id`), `professional_credential` (**sin** `organization_id`; política por `user_id` o admin de la clínica del miembro) (**2**) | 11 + 2 = las **13** tablas con RLS |
| 3. Excepción por clínica (**con** `organization_id`, **sin** RLS) | `organization_settings`, `organization_branding` (`ADR-0003`) · **`invitations` (sin ADR)** | `settings`/`branding`: la PK **es** el `organization_id`. `invitations`: PK propia, acceso por id, por organización y por hash de token |
| 4. Sin `organization_id` | `organizations`, `users`, `refresh_tokens`, `password_reset_tokens`, `platform_admins`, `cie10_code`, `dental_finding_catalog`, `app_metadata` | tenant raíz, identidad global, catálogos globales. `flyway_schema_history` la crea Flyway: se excluye por nombre |

Nota: el catálogo CIE se llama `cie10_code`; en conversaciones previas se abrevió `cie`.

**`invitations`, hoy (código leído, no inferido):**
- `Invitation` mapea `organization` con `@ManyToOne`; **no** lleva `@TenantId` (las 11 de la lista 1 sí).
- Listado: `findByOrganizationIdOrderByCreatedAtDesc(OrganizationContext.get())` — filtro **en la consulta**.
- `resend`/`revoke`: `findByIdForUpdate(id)` **sin filtro de organización** y luego `.filter(i -> i.getOrganization().getId().equals(OrganizationContext.get()))` → 404. Correcto, pero **lee y bloquea la fila de otra clínica antes de rechazarla** y depende de que cada método nuevo repita el filtro.
- Alta/aceptación: `findByTokenHash(...)` y `findByTokenHashForUpdate(...)` **sin organización** y **sin contexto** (el invitado aún no tiene sesión): es el motivo por el que meterle RLS no es trivial.

**`ADR-0003` frente a `platform-admin`:** `PlatformOrganizationController` expone un solo `POST /platform/organizations` (`hasRole('PLATFORM_ADMIN')`) con `name` y `adminEmail`. `PlatformOrganizationService.provision` **crea** la organización, y luego `settings.save(OrganizationSettings.defaultsFor(organization))` con la organización que acaba de crear: el `organizationId` **no viene de la petición**. No hay otro endpoint de plataforma que lea o escriba settings/branding. Las condiciones de invalidación de `ADR-0003` ("un endpoint acepte el `organizationId` desde la petición" / "la consulta deje de ser por PK") **no se cumplen**; sí cambia que la invariante literal ("el id **siempre** sale del token firmado") ya no es cierta: aquí el servicio de plataforma escribe sin `OrganizationContext`.

## Goals / Non-Goals

**Goals**
- Una **lista única, ejecutable** de clasificación de tablas que rompe la build ante una tabla nueva sin clasificar.
- `invitations` y la revalidación de `ADR-0003` **decididas por escrito**.

**Non-Goals**: ver `proposal.md` (sin RLS nueva, sin migraciones, sin tocar producción ni `appointments`).

## Decisions

### D1. Test de inventario sobre el catálogo, como rol de aplicación
`TenantTableInventoryIT extends AbstractRlsIntegrationTest` abre una conexión con `appConnection()` (rol `odontorisas_app`, `NOSUPERUSER NOBYPASSRLS`). Consultas:
- tablas: `pg_class` (`relkind IN ('r','p')`, esquema `public`), con `relrowsecurity` y `relforcerowsecurity`;
- columna: `pg_attribute` (`attname = 'organization_id'`, `NOT attisdropped`);
- políticas: `pg_policies` (`schemaname = 'public'`), contadas por tabla.

⚠️ **No `information_schema.columns`**: solo muestra columnas de tablas sobre las que el rol tiene algún privilegio; una tabla sin `GRANT` para `odontorisas_app` **desaparecería del inventario** y la prueba pasaría vacía justo donde más importa. `pg_class`/`pg_attribute` no filtran por privilegio. Se excluye `flyway_schema_history` por nombre.

El IT es fino: solo extrae una instantánea (`List<TableInfo>`: nombre, `hasOrgId`, `rlsEnabled`, `rlsForced`, `policyCount`).

### D2. La clasificación es una función pura, con las listas como datos
`TenantTableClassification.evaluate(List<TableInfo>) → List<Violation>` es una clase de test **sin Spring ni Docker**. Las cuatro listas viven en ella como `Map<String, String>` (tabla → **motivo**, y para la lista 3 también el ADR). Reglas (cada una nombra la tabla y la regla):
1. **sin clasificar**; 2. **entrada obsoleta**; 3. **en dos listas**; 4. lista 1/2 sin RLS habilitada, **forzada** o sin política; 5. lista 1 sin `organization_id`; 6. lista 3 con RLS (debe migrar a la 1); 7. lista 3 sin `organization_id` (mal clasificada); 8. lista 4 con `organization_id`.

Una excepción **no se puede quedar callada**: pasar de lista 3 a RLS obliga a editar la lista (regla 6), igual que una columna `organization_id` en una tabla de la lista 4 (regla 8).

### D3. Se prueba fallando con instantáneas sintéticas
`TenantTableClassificationTest` (JUnit, **corre sin Docker**) construye instantáneas con **una violación por regla** y comprueba que `evaluate` la devuelve nombrando tabla y regla, más una instantánea **correcta** que devuelve vacío. Así la regla "se ve fallar" en cada build, no solo una vez a mano (`docs/testing.md §5`). Al aplicar, además, se crea a mano una tabla sin clasificar en una migración local, se ve romper el IT y se revierte (task 5).

El IT incluye su propio guardián de importador vacío: la instantánea **debe** contener `patients` e `invitations`.

### D4. `invitations`: excepción documentada, no RLS (en este change)
Se clasifica en la lista 3 con motivo y ADR-0022. **No** se le agrega RLS aquí porque:
- aceptar una invitación ocurre **sin sesión** (se llega por `tokenHash`, sin `app.organization_id`): una política `organization_id = current_setting(...)` bloquearía el alta, y una política con rama "por hash" necesita diseño propio (¿función `SECURITY DEFINER`? ¿rol aparte?) y pruebas como el rol de Neon;
- es una **migración sobre una tabla en producción** con el riesgo ya vivido (`migraciones-neon-dueno-no-superusuario`: la RLS forzada aplica al dueño en Neon y no en CI).

El ADR declara el **riesgo** (no hay barrera de motor: una consulta nueva sobre `invitations` que olvide el filtro filtraría hashes y correos de otras clínicas) y la **condición que invalida la excepción**: cualquier endpoint que acepte el id de una invitación o de una organización desde la petición sin la comparación posterior; cualquier consulta nueva sobre `invitations` sin filtro de organización; o la decisión de pasar a la opción B tras un explore.

### D5. `ADR-0003`: válida con matiz, conclusión con evidencia y límites
Se concluye **válida con matiz**, con lo verificado (un único endpoint de plataforma; id generado por el servicio; inserción por PK) y lo **no** verificado (que no haya otros caminos futuros: el ADR reescribe la invariante como *"el `organizationId` viene del token firmado **o lo genera el propio servicio al crear la organización**; nunca de la petición"*). `ADR-0003` solo anota su estado.

### D6. El inventario es un candado de **clasificación**, no de corrección
Que una tabla esté en la lista 1 con RLS forzada **no prueba** que su política sea correcta; eso lo siguen probando los `*RlsIT` por capacidad (obligatorios). El inventario garantiza que **ninguna tabla escapa a la decisión**, no que cada decisión sea buena. Se dice así en `docs/testing.md`.

## Risks / Trade-offs

- ⚠️ **La lista puede volverse ritual** (agregar la tabla "para que pase"). Mitigación: cada entrada exige **motivo**, y la lista 3 exige ADR; aparece en el diff.
- ⚠️ **El motivo es texto libre** y no se valida. Aceptado.
- ⚠️ **`appointments`** (en apply) romperá el test hasta que se declare; es su función. Quien llegue segundo la declara en la lista 1 (con `organization_id`, `@TenantId` y RLS forzada) — task 4.
- ⚠️ **El IT requiere Docker** (`disabledWithoutDocker=true`): sin Docker se salta. La función pura **no**, así que la lógica se prueba siempre; el catálogo real, solo con Docker/CI.
- ⚠️ **Conclusión sobre `invitations` y `ADR-0003` basada en lectura de código**, no en un pentest. Dicho en el ADR.

## Alternativas descartadas

- **Opción B ahora (RLS en `invitations`)**: ver D4. Se reevalúa con el inventario ya en pie y un explore del acceso por token.
- **Solo documentar la lista en un ADR**: es el estado actual y lo que `docs/testing.md §5` enseña que no se sostiene.
- **Leer `information_schema`**: oculta tablas sin privilegios (D1).
- **Exigir RLS a *toda* tabla**: falso para identidad global, catálogos y el tenant raíz; ya decidido en `ADR-0003` y `architecture.md`.
- **ArchUnit sobre entidades (`@TenantId` presente)**: vigila el mapeo JPA, no el esquema; una tabla creada solo por migración (como `invitations`, sin `@TenantId`) pasaría. El catálogo de la base es la fuente que importa.
