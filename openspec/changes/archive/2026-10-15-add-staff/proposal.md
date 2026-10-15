## Why

Desde que el registro es solo por invitación, el `ADMIN` de una clínica invita a su personal a ciegas: no ve qué
invitaciones siguen pendientes, cuáles vencieron ni quién ya entró, y si el invitado no ingresa el código en 10
minutos la invitación queda inutilizable, sin forma de reenviarla. La pantalla "Personal" actual solo invita y
lista nombres, aunque el backend ya permite revocar invitaciones y desactivar miembros.

## What Changes

- Nueva capacidad **`staff`** (fila 2 de `docs/vision.md`): la gestión del personal de la clínica por su `ADMIN`.
- Pantalla **Personal** con dos pestañas y un botón **Invitar** común:
  - **Personal**: miembros de la organización activa con rol y estado (activo / inactivo), búsqueda por nombre o
    correo, y **desactivar / reactivar** con confirmación.
  - **Invitaciones**: las emitidas por la organización con su estado (pendiente, aceptada, revocada, vencida),
    filtro por estado, **reenviar** y **revocar**.
- **Reenviar** una invitación pendiente o vencida: nuevo enlace y nuevo código, anula los anteriores y renueva
  la vigencia. Resuelve el código de 10 minutos que vence sin reenvío.
- API nueva: `GET /staff/invitations` y `POST /staff/invitations/{id}/resend`. Los endpoints existentes
  (`POST /staff/invitations`, `GET /staff/members`, `DELETE /staff/invitations/{id}`,
  `PATCH /staff/members/{id}/status`) pasan a estar especificados por esta capacidad.
- Correcciones de errores que hoy responden **500** en vez de RFC 9457:
  - invitar a un correo con una invitación pendiente → `409`;
  - cambiar el estado de la propia cuenta → `409`; de alguien que no es miembro → `404`;
  - revocar una invitación ya aceptada o revocada → `409` (hoy se permite).

## Capabilities

### New Capabilities
- `staff`: gestión del personal de una clínica por su `ADMIN`: invitar, ver y administrar las invitaciones
  emitidas (reenviar, revocar), ver a los miembros y su estado, y desactivarlos o reactivarlos. Opera sobre
  `Invitation` y `Membership` (entidades de `multi-tenancy`); el alta por invitación sigue en `authentication`.

### Modified Capabilities
<!-- Ninguna: el registro con invitación (authentication) y el modelo de membresía (multi-tenancy) no cambian. -->

## Non-goals

- **Perfil profesional y colegiatura** (`ADR-0016` D7): lo que habilitará el acto clínico. Será un `update-staff`.
- **Cambiar el rol** de un miembro (`ADMIN` ↔ `MEMBER`): un `ADMIN` de clínica solo emite `MEMBER` hoy.
- **Eliminar** miembros o invitaciones: se desactiva y se revoca; nada se borra (trazabilidad, `docs/domain.md`).
- **Administrar invitaciones `ADMIN`** desde la clínica: las emite la plataforma; aquí solo se ven.
- Panel del `PLATFORM_ADMIN` (sigue por API, `docs/deployment.md` §11).

## Impact

- **Backend**: `StaffService` (listado y reenvío de invitaciones, errores tipados), `StaffController`, DTO de
  invitación para el listado, `InvitationRepository`. Sin migraciones: el reenvío reutiliza las columnas de la
  invitación (hash del token, código, vencimientos, intentos).
- **Contrato**: `contracts/openapi.json` y cliente orval regenerados.
- **Frontend**: módulo `staff` (pantalla con pestañas, tablas, diálogos de confirmación, schema Zod del
  formulario de invitación); `core/ui` suma `tabs` y `badge` de shadcn (`docs/frontend.md` §4.1). La screen queda
  fina (`docs/frontend.md` §1).
- **Correo**: el reenvío usa el mismo `InvitationMailer` y su plantilla.
- **Docs**: `docs/domain.md` (estado derivado "vencida", reenvío), `docs/vision.md` (estado de `staff`).
