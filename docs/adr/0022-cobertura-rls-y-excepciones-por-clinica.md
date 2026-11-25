# ADR-0022: Cobertura de RLS — cada tabla clasificada, `invitations` como excepción conocida

- **Estado:** Aceptado (decisión); se implementa en `update-multi-tenancy-rls-coverage`
- **Fecha:** 2026-11-24
- **Decisores:** David Velarde Alvarado
- **Relacionado:** [`ADR-0003`](0003-excepcion-rls-metadatos-del-tenant.md) (revalidada aquí) · [`ADR-0019`](0019-proteccion-de-datos-ley-29733.md) (Dentdesk es el *encargado*) · `openspec/specs/multi-tenancy` · `docs/backend.md §8.1` · `docs/testing.md §5`
- **Fuente normativa:** no aplica. Este ADR **no cita artículos** ni afirma cumplimiento; registra un riesgo técnico. `invitations` guarda correos del personal (dato personal) y hashes de credenciales de un solo uso.

## Contexto

Se sostenía que *"toda entidad de negocio lleva `organization_id` + RLS"*, con excepciones documentadas en distintos sitios. Al auditar las migraciones V1–V31 (y recalcular con V32) se vio que **la lista de excepciones estaba incompleta** y que **nada impide que crezca sin que nadie lo decida**:

- **`invitations`** tiene `organization_id`, no tiene RLS y **ningún ADR la menciona**.
- **`ADR-0003`** se escribió antes de `platform-admin`, y sus condiciones de invalidación hablan precisamente de un panel de superadministrador.
- No hay control que detecte una **tabla sin clasificar** (`appointments`, V32, entró bien declarada, pero nada lo exigía).

## Decisión

### D1. Cada tabla pertenece a una de cuatro listas, cada función que salta la RLS a una quinta, y la build lo comprueba
Un test de inventario (`TenantTableInventoryIT`, como rol de aplicación no superusuario, leyendo `pg_class`/`pg_attribute`/`pg_policies`) exige que **toda** tabla de `public` esté en exactamente una lista, y que las listas coincidan con la realidad:

| Lista | Tablas (estado a 2026-11-24, con V32) | Exige |
|---|---|---|
| **1. Por organización** | `patients`, `historia_clinica`, `clinical_history_diagnosis`, `clinical_background_entry`, `consultation_note`, `consultation_note_correction`, `consultation_note_diagnosis`, `consultation_note_finding`, `dental_chart_block`, `dental_chart_finding`, `dental_chart_finding_tooth`, `appointments` (12) | `organization_id` + RLS habilitada, **forzada** y con política |
| **2. Por identidad** | `memberships`, `professional_credential` | RLS habilitada, forzada y con política (clave `user_id`) |
| **3. Excepción por clínica** | `organization_settings`, `organization_branding` (`ADR-0003`) · `invitations` (**D2**) | `organization_id`, **sin** RLS; motivo + ADR |
| **4. Sin `organization_id`** | `organizations`, `users`, `refresh_tokens`, `password_reset_tokens`, `platform_admins`, `cie10_code`, `dental_finding_catalog`, `app_metadata` | no tener `organization_id` |

Además, una **quinta lista** declara las funciones `SECURITY DEFINER` de `public` que no son de una extensión (hoy `current_user_is_org_admin`, V24, y `clinic_capacity_facts`, V32), con motivo y los tests que las cubren, y exige que fijen `search_path`. Son la única vía para leer por encima de la RLS del rol de aplicación: una nueva no entra sin declararse. Se identifican por firma (una sobrecarga es otra entrada) y su `search_path` debe ser exactamente un valor permitido (hoy `public`).

El inventario también **falla ante lo que no clasifica**: una vista o vista materializada en `public`, cualquier objeto en otro esquema de usuario, y un rol de lectura con `SUPERUSER` o `BYPASSRLS`, o que pueda asumir con `SET ROLE` un rol que los tenga (esos atributos no se heredan por membresía; lo que importa es poder asumirlos). También falla si ese rol es dueño de una tabla de `public` o puede asumir a su dueño: con `FORCE` el dueño no salta la RLS, pero V32 le dio una política de solo lectura sobre `memberships` y `professional_credential`, así que actuar como él lee esas filas de todas las clínicas. Hoy no existe ninguno de estos casos. Quedan fuera, dicho: secuencias (no guardan filas de clínicas), `odontorisas_backup` (`BYPASSRLS` deliberado) y el rol de producción, que la CI no alcanza (chequeo manual en `docs/deployment.md`).

Las 14 tablas con RLS son 12 + 2. `flyway_schema_history` se excluye (la crea Flyway).

### D2. `invitations`: excepción por clínica documentada; **sin RLS en este change**
**Estado real (código leído):** la entidad no lleva `@TenantId`; el listado filtra por `OrganizationContext` en la consulta; `resend`/`revoke` cargan por id y comparan la organización **después** de leer y bloquear la fila (404 si es de otra clínica); la aceptación llega por `tokenHash` **sin sesión ni organización**.

**Por qué no RLS ahora:** aceptar una invitación ocurre antes de que exista contexto de organización, así que una política por `organization_id` bloquearía el alta, y una política con rama "por hash" exige un diseño propio y una migración sobre una tabla en producción, con el antecedente de que en Neon la RLS forzada aplica al dueño y en CI no (`docs/deployment.md`). Es la **opción B**, y queda para un `/opsx:explore`.

**Riesgo asumido, dicho sin adornos:** **no hay barrera de motor.** Una consulta nueva sobre `invitations` que omita el filtro expondría hashes de token y correos de otras clínicas. Hoy el código es correcto; la protección es **solo** que cada método repita la comprobación.

**Esta excepción deja de valer —y hay que poner RLS o rediseñar— si:**
- un endpoint acepta desde la petición el id de una invitación o de una organización **sin** la comparación posterior con `OrganizationContext`;
- se agrega una consulta sobre `invitations` sin filtro de organización (listados, informes, `JOIN`);
- se guardan en la tabla datos más sensibles que correo, rol y hashes;
- un explore del acceso por token concluye que la opción B es viable.

**Observación, sin cambiar producción:** `findByIdForUpdate` bloquea una fila de otra clínica antes de rechazarla. No filtra datos (el 404 es idéntico), pero es una lectura cruzada que la RLS impediría.

### D3. `ADR-0003` revalidada: **válida con matiz**
**Verificado leyendo el código:** `PlatformOrganizationController` expone un solo `POST /platform/organizations` (`hasRole('PLATFORM_ADMIN')`) con `name` y `adminEmail`; `PlatformOrganizationService.provision` **crea** la organización y guarda sus `OrganizationSettings` por esa misma entidad. El `organizationId` **no viene de la petición**, y el acceso a `settings`/`branding` sigue siendo por PK. Ninguna condición de invalidación de `ADR-0003` se cumple.

**El matiz:** la invariante literal de `ADR-0003` ("el `organizationId` siempre sale del token firmado") **ya no es cierta**: el servicio de plataforma escribe sin `OrganizationContext`. Se reescribe como: *el `organizationId` con que se leen o escriben `organization_settings`/`organization_branding` sale del token firmado **o lo genera el propio servicio al crear la organización**; nunca de la petición, el cuerpo o una cabecera.*

**Lo que NO se verificó:** que no aparezcan caminos nuevos de plataforma (p. ej. "editar clínica", "suspender"); cada uno deberá releer la invariante.

### D4. El inventario garantiza **clasificación**, no **corrección**
Que una tabla esté en la lista 1 no prueba que su política sea buena; eso lo siguen probando los `*RlsIT` por capacidad. El inventario asegura que **ninguna tabla escapa a la decisión** y que una excepción no pasa a RLS (o viceversa) sin que alguien edite la lista.

## Consecuencias

- ✅ Una tabla nueva **no entra sin clasificarse**; una excepción no cambia de estado en silencio.
- ✅ `invitations` deja de ser un olvido: es una decisión escrita, con su riesgo y su condición de invalidez.
- ✅ `ADR-0003` revalidada contra producción real.
- ⚠️ **`invitations` sigue sin barrera de motor.** Documentarlo no lo cierra.
- ⚠️ La lista puede volverse ritual (agregar la tabla "para que pase"): cada entrada exige motivo y la lista 3 exige ADR.
- ⚠️ El IT requiere Docker; la lógica de clasificación se prueba siempre, sin Docker.
- ⚠️ La conclusión sobre `invitations` y `ADR-0003` viene de **lectura de código**, no de una prueba de intrusión.

## Alternativas descartadas

- **Opción B ahora (RLS en `invitations`)** — diseño de acceso por token sin contexto + migración sobre producción; se reevalúa tras un explore.
- **Solo documentar la lista** — es el estado de partida y lo que `docs/testing.md §5` enseña que no se sostiene.
- **Leer `information_schema.columns`** — oculta las tablas sobre las que el rol no tiene privilegios, justo las que importa detectar.
- **Exigir RLS a toda tabla** — falso para identidad global, catálogos y el tenant raíz (`ADR-0003`, `architecture.md`).
- **ArchUnit sobre `@TenantId`** — vigila el mapeo JPA, no el esquema; `invitations` pasaría porque no lleva `@TenantId` y tampoco debía.
