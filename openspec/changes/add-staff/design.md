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
`PENDING` con `expiresAt <= clock.instant()` se devuelve como `EXPIRED` (el `Clock` inyectado en `StaffService`,
nunca `Instant.now()`, para que los tests controlen el tiempo). El DTO de respuesta lleva un enum propio de
presentación (`PENDING | ACCEPTED | REVOKED | EXPIRED`) distinto del de la entidad, y un `boolean manageable`
calculado en el backend (`role == MEMBER` y estado `PENDING` o `EXPIRED`): la regla de D3 vive en un solo lugar y
el front solo decide si muestra las acciones.

Consecuencia: una invitación vencida **sigue ocupando** el correo. Invitar de nuevo ese correo responde
`409 invitation-already-pending` con un mensaje que dice "pendiente o vencida" y sugiere reenviarla desde la lista.

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
- La fila se carga con bloqueo (D7) y el estado se comprueba **después** de bloquear.

### D3 — Qué invitaciones gestiona la clínica

Reenviar y revocar aplican solo a invitaciones `MEMBER` en `PENDING` (vencidas incluidas). Las `ADMIN` las emite la
plataforma: se listan, pero cualquier acción sobre ellas responde `403`. `ACCEPTED` o `REVOKED` → `409`.
Una invitación de otra organización → `404` (no se revela que existe), igual que un id inexistente.

Respuestas: revocar (`DELETE /staff/invitations/{id}`) y cambiar estado (`PATCH /staff/members/{id}/status`) pasan
de `200` vacío a **`204 No Content`** (mismo criterio que `POST /auth/logout`); el cliente generado no usa el
cuerpo, así que no rompe nada.

### D4 — Errores tipados del módulo

Excepciones `BusinessException` nuevas en `service.staff`, una por `type`:

| Caso | Status | `type` |
|---|---|---|
| Invitación pendiente o vencida para el mismo correo | 409 | `invitation-already-pending` |
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
- `react/no-multi-comp`: cada fila, badge o estado vacío en su propio archivo (`MemberRow`, `InvitationRow`, …).
- **Sin `frontend-design`**: es una pantalla dentro del producto autenticado; se compone con el sistema de diseño
  existente (`docs/frontend.md` §4, §4.1–§4.6) y los tokens del tema.

### D7 — Concurrencia sobre una invitación

Reenviar, revocar y verificar el correo **escriben** la fila de la invitación, y el registro la **consume**. Sin
coordinación, Hibernate escribe todas las columnas con lo que leyó y se pisan (regla de `docs/backend.md` §8.3.1):

- **Reenvío contra registro:** el registro ya validó el token viejo y su `UPDATE ... WHERE status = 'PENDING'`
  espera; el reenvío confirma dejándola en `PENDING` con otro hash, y el `UPDATE` igual acepta el enlace anulado.
- **Verificación contra reenvío:** la verificación cargó la fila antes del reenvío y, al confirmar después,
  reescribe el hash viejo: el reenvío queda deshecho.
- **Reenvío contra registro ya confirmado:** sin bloqueo, el reenvío reescribe `status = PENDING` sobre una
  invitación `ACCEPTED`.

Reglas:
1. `resend`, `revoke` y `verifyEmail` cargan la invitación con `@Lock(PESSIMISTIC_WRITE)`
   (`findByIdForUpdate`, `findByTokenHashForUpdate`) como **primera** lectura de esa entidad en la transacción, y
   comprueban estado y vigencia **después** de bloquear.
2. `consume()` exige también el hash del token: `WHERE id = :id AND token_hash = :hash AND status = 'PENDING' AND
   expires_at > :now`. Si un reenvío cambió el hash mientras esperaba, actualiza 0 filas → invitación inválida.
3. Se prueba con hilos reales y orden forzado (`AbstractIntegrationTest.awaitLockWaitOrFinished`), primero contra
   el código sin el fix para ver el fallo.

`consume` y `verifyEmail` son del flujo de `authentication`; endurecerlos no cambia su comportamiento observable,
solo cierra la carrera.

## Risks / Trade-offs

- **[Sesión en curso de un desactivado]** El access token sigue valiendo hasta su vencimiento (`JWT_ACCESS_TTL`,
  15 min); el refresh sí se rechaza al instante. → Aceptado y documentado: el token no consulta la base en cada
  petición por diseño. Si hiciera falta corte inmediato, sería un change de `authentication`.
- **[Reenvío repetido como spam]** Un `ADMIN` podría reenviar muchas veces al mismo correo. → El correo solo lo
  dispara el `ADMIN` de esa clínica y queda trazado; un límite de frecuencia se agrega si aparece el abuso.
- **[Carreras sobre la invitación]** Reenvío, revocación, verificación y registro concurrentes. → D7: bloqueo
  pesimista y hash en la condición de `consume`; si gana el registro, el reenvío responde `409`; si gana el
  reenvío, el enlace viejo queda rechazado.
- **[Conexión retenida durante el SMTP]** El correo sale con la fila bloqueada y la conexión tomada (pool de 5).
  → El SMTP tiene tope de 10 s (`MAIL_TIMEOUT`); el bloqueo es de una sola fila y el reenvío es una acción manual
  y poco frecuente.

## Migration Plan

Sin migraciones de base. Deploy normal de backend y frontend; el contrato y el cliente generado se regeneran en el
mismo PR. Rollback: revertir el PR (las filas reenviadas siguen siendo invitaciones válidas para la versión
anterior).
