## Contexto

El registro actual crea `Organization + OrganizationSettings + User + Membership(ADMIN)` sin autorización externa. El nuevo registro no debe inferir una clínica por nombre ni crear un tenant desde una URL pública.

## Decisiones

### D1 — El token es la autorización

La organización y el rol se obtienen de la invitación almacenada, nunca de campos enviados por el visitante. El token será aleatorio, opaco y de alta entropía; la base guardará solo su hash.

### D2 — Invitación nominativa y verificación de correo

Cada invitación tendrá correo obligatorio normalizado, rol, organización, estado, expiración, emisor y fechas de consumo. El enlace por sí solo no bastará: el sistema enviará un código o enlace de verificación al correo invitado y no creará la cuenta hasta verificarlo.

### D3 — Roles según emisor

Un `ADMIN` de clínica solo puede emitir invitaciones `MEMBER` para su organización activa. Una invitación `ADMIN` solo podrá ser emitida por `PLATFORM_ADMIN`, cuya implementación está en `add-platform-admin`.

### D4 — Consumo atómico

La aceptación revalida el token dentro de una transacción y consume la invitación con una actualización condicional `WHERE status = 'PENDING'`. Si dos solicitudes compiten, exactamente una crea la membresía.

### D5 — Rutas y permisos

La aceptación y validación viven bajo `/auth/**` y son públicas. La emisión para una clínica vive bajo `/staff/invitations` y exige sesión `ADMIN`; nunca se colocará una ruta de emisión bajo `/auth/**`.

### D6 — USER pasa a roles de personal

El modelo acordado es `ADMIN | MEMBER`. Como la base desplegada solo contiene datos de prueba, una migración cambia `memberships.role = 'USER'` a `MEMBER` y agrega un `CHECK` para impedir valores futuros inválidos. No habrá convivencia temporal.

### D7 — Correo desacoplado

La lógica usará `InvitationMailer`. La infraestructura podrá usar Spring Mail con SMTP; el proveedor concreto se configura por entorno. Los tests usarán un stub.

### D8 — Despliegue

La migración de invitaciones, el cambio de rol y el código que exige invitaciones se desplegarán juntos. No se ejecutará la migración en producción sin el backend compatible.

## Datos existentes

Las organizaciones de prueba no se fusionan ni se borran automáticamente. La migración convierte `USER` a `MEMBER`. La limpieza de organizaciones vacías puede hacerse manualmente después de revisar membresías y datos de negocio.

## Open questions

Ninguna bloqueante para este change. El bootstrap y el modelo de `PLATFORM_ADMIN` son decisiones del change separado `add-platform-admin`.

