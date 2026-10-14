## 1. Contrato y modelo

- [x] 1.1 Definir DTOs, estados, expiración por defecto y errores RFC 9457.
- [x] 1.2 Cambiar `Role.USER` a `Role.MEMBER` y auditar backend, frontend y tests.
- [x] 1.3 Actualizar OpenAPI y regenerar el cliente.

## 2. Persistencia

- [x] 2.1 Crear tabla de invitaciones con organización, hash, correo obligatorio, rol, estado, expiración, emisor y consumo.
- [x] 2.2 Migrar `memberships.role` de `USER` a `MEMBER` y agregar restricción `ADMIN | MEMBER`.
- [x] 2.3 Crear entidades/repositorios y consumo condicional atómico.
- [x] 2.4 Verificar migraciones contra PostgreSQL real.

## 3. Emisión

- [x] 3.1 Implementar `POST /staff/invitations` para `ADMIN` de la organización activa.
- [x] 3.2 Impedir que un `ADMIN` emita rol `ADMIN` o invite a otra organización.
- [x] 3.3 Generar token aleatorio, guardar SHA-256 y devolverlo solo una vez.
- [x] 3.4 Implementar expiración, revocación y rechazo de invitación duplicada pendiente.

## 4. Registro

- [x] 4.1 Eliminar de `AuthService.register` la creación de `Organization` y `OrganizationSettings`.
- [x] 4.2 Hacer obligatorio el token en `/auth/register`.
- [x] 4.3 Implementar `/auth/invitations/validate` sin crear datos.
- [ ] 4.4 Verificar token, correo y código de email antes de crear identidad/membresía. (Pendiente del proveedor de correo.)
- [x] 4.5 Crear membresía con el rol de la invitación y emitir JWT con el `oid` correcto.
- [x] 4.6 Rechazar token inválido, expirado, revocado, consumido o de otro correo.
- [x] 4.7 Rechazar correos ya registrados sin duplicar identidad.

## 5. Correo y frontend

- [ ] 5.1 Crear `InvitationMailer` y adaptador SMTP externalizado.
- [ ] 5.2 Crear plantillas de invitación/verificación y stub para tests.
- [x] 5.3 Mostrar registro solo con `?token=`; sin campo para crear/buscar clínicas.
- [ ] 5.4 Mostrar organización, rol y correo solo después de validar el token.
- [x] 5.5 Regenerar cliente API y añadir pantalla inicial de gestión de personal.

## 6. Pruebas y cierre

- [ ] 6.1 Probar autorización, expiración, revocación, replay y correo distinto.
- [ ] 6.2 Probar concurrencia y aislamiento tenant del `oid`.
- [x] 6.3 Actualizar documentación.
- [x] 6.4 Ejecutar `openspec validate update-registration-organization-linking --strict`.
- [ ] 6.5 Ejecutar `mvn verify`, validación frontend y pruebas de contrato.

