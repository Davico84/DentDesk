# Design

## Context

Ver `proposal.md` (Why). Estado actual observado en el código:

- `StaffController` (`/staff/**`, `@PreAuthorize("hasRole('ADMIN')")`) ya expone invitar, listar miembros, revocar y
  cambiar el estado de un miembro. No hay listado de invitaciones ni reenvío.
- `StaffService` opera siempre sobre la organización del token (`OrganizationContext`). Tres caminos lanzan
  `IllegalStateException` / `IllegalArgumentException`, que el `GlobalExceptionHandler` convierte en **500**:
  invitación pendiente duplicada, cambiar el estado propio y cambiar el de un no miembro. `revoke` no comprueba el
  estado (permite "revocar" una aceptada) y un id inexistente responde `400 invalid-invitation`.
- `Invitation` ya tiene todo lo que necesita el reenvío: `tokenHash`, `verificationCodeHash`,
  `verificationExpiresAt`, `verificationAttempts`, `verifiedAt`, `expiresAt`, `status`. El token y el código solo
  viajan por correo (`InvitationMailer`), la base guarda hashes.
- El login y el refresh ya exigen membresía `ACTIVE` (`AuthService.activeMembership`): desactivar a alguien ya le
  impide entrar.
- Front: `StaffSettingsScreen` → `StaffManagement`, un componente con formulario sin Zod y la lista de miembros.
  `core/ui` no tiene `tabs` ni `badge`.

## Goals / Non-Goals

**Goals:**
- Que todo error del módulo `staff` responda en RFC 9457 con un `type` que el front pueda distinguir.
- Reenvío que anula de verdad el enlace anterior, sin migración de base.
- Pantalla construida con las reglas de `docs/frontend.md` (screen fina, genéricos en `core`, validación en Zod).

**Non-Goals:**
- Paginación del lado del servidor: una clínica tiene decenas de miembros e invitaciones, no miles.
- Notificar al miembro desactivado o cerrar su sesión en curso al instante (ver Riesgos).

## Decisions

### D1 — El estado "vencida" se deriva, no se persiste

`EXPIRED` no existe en `invitations.status` (`PENDING | ACCEPTED | REVOKED`). El listado lo calcula:
`PENDING` con `expiresAt <= ahora` se devuelve como `EXPIRED`. El DTO de respuesta lleva un enum propio de
presentación (`PENDING | ACCEPTED | REVOKED | EXPIRED`) distinto del de la entidad.

- *Alternativa descartada:* un job que marque vencidas. Agrega una pieza programada (que en Render free duerme)
  para un dato que se calcula gratis al leer.

### D2 — Reenviar reescribe la misma invitación

`POST /staff/invitations/{id}/resend` genera token y código nuevos y los guarda **en la misma fila**:
`tokenHash` y `verificationCodeHash` nuevos, `expiresAt = ahora + 7 días`, `verificationExpiresAt = ahora + 10 min`,
`verificationAttempts = 0`, `verifiedAt = null`, `status = PENDING`. Como el registro busca por `tokenHash`, el
enlace anterior deja de existir en el mismo commit. Mismo patrón que la emisión: `saveAndFlush` y después el
correo, dentro de la transacción (D6 de `add-platform-admin`): si el SMTP falla, el rollback deja intactos el hash
y el código anteriores.

- *Alternativa descartada:* revocar la vieja y crear una nueva. El listado mostraría dos filas por persona y
  perdería la fecha original de emisión; y la restricción "una pendiente por correo" tendría una ventana.
- Respuesta: `200` con la invitación ya actualizada (mismo DTO del listado), sin token.

### D3 — Qué invitaciones gestiona la clínica

Reenviar y revocar aplican solo a invitaciones `MEMBER` en `PENDING` (vencidas incluidas). Las `ADMIN` las emite la
plataforma: se listan, pero cualquier acción sobre ellas responde `403`. `ACCEPTED` o `REVOKED` → `409`.
Una invitación de otra organización → `404` (no se revela que existe), igual que un id inexistente.

### D4 — Errores tipados del módulo

Excepciones `BusinessException` nuevas en `service.staff`, una por `type`:

| Caso | Status | `type` |
|---|---|---|
| Invitación pendiente duplicada | 409 | `invitation-already-pending` |
| Invitación inexistente o de otra clínica | 404 | `invitation-not-found` |
| Reenviar/revocar aceptada o revocada | 409 | `invitation-not-pending` |
| Cambiar el estado propio | 409 | `cannot-change-own-status` |
| Miembro inexistente o de otra clínica | 404 | `member-not-found` |

Los `AccessDeniedException` existentes (rol `ADMIN` del destino, invitación `ADMIN`) siguen dando `403`.

### D5 — Búsqueda y filtro en el cliente

`GET /staff/members` y `GET /staff/invitations` devuelven la lista completa de la organización; la búsqueda (sin
tildes ni mayúsculas, `normalize("NFD")`) y el filtro por estado con sus contadores se hacen en el front sobre
datos ya cargados. Con el volumen de una clínica es instantáneo y evita parámetros de consulta que habría que
documentar y probar.

- *Alternativa descartada:* filtros en la API. Se vuelve necesaria recién con paginación (fuera de alcance).

### D6 — Estructura del front

- `screens/settings/StaffSettingsScreen.tsx` queda en una línea: `<StaffManagement />`.
- `modules/staff/components/`: `StaffManagement` (cabecera + botón Invitar + pestañas), `InviteStaffDialog`
  (formulario con schema Zod en `modules/staff/schemas/`), `MembersTab`, `InvitationsTab`, `MemberStatusBadge`,
  `InvitationStatusBadge`. Las acciones destructivas usan el `ConfirmDialog` de `core/ui`.
- `core/ui` suma `tabs` y `badge` con `pnpm dlx shadcn@latest add tabs badge` (nunca `init`; verificar que no tocó
  `globals.css`). Los badges de estado usan tokens del tema, no colores fijos.
- La pestaña activa vive en la URL (`?tab=invitaciones`) para poder enlazarla y que sobreviva a una recarga.
- Hooks de React Query en `modules/staff/hooks/useStaffApi.ts` con claves separadas (`["staff","members"]`,
  `["staff","invitations"]`); invitar, reenviar y revocar invalidan las invitaciones; cambiar estado, los miembros.

## Risks / Trade-offs

- **[Sesión en curso de un desactivado]** El access token sigue valiendo hasta su vencimiento (`JWT_ACCESS_TTL`,
  15 min); el refresh sí se rechaza al instante. → Aceptado y documentado: el token no consulta la base en cada
  petición por diseño. Si hiciera falta corte inmediato, sería un change de `authentication`.
- **[Reenvío repetido como spam]** Un `ADMIN` podría reenviar muchas veces al mismo correo. → El correo solo lo
  dispara el `ADMIN` de esa clínica y queda trazado; un límite de frecuencia se agrega si aparece el abuso.
- **[Reenvío y verificación concurrentes]** Si el invitado verifica justo mientras se reenvía, el reenvío pisa
  `verifiedAt`. → El invitado recibe el correo nuevo con su código; es el mismo efecto que pidió el `ADMIN`.

## Migration Plan

Sin migraciones de base. Deploy normal de backend y frontend; el contrato y el cliente generado se regeneran en el
mismo PR. Rollback: revertir el PR (las filas reenviadas siguen siendo invitaciones válidas para la versión
anterior).
