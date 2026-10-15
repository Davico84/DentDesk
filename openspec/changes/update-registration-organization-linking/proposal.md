# Cambio: Registro únicamente mediante invitación

## Problema

`/auth/register` crea actualmente una organización nueva, su configuración, un usuario y una membresía `ADMIN` para cualquier visitante. El nombre de la clínica no identifica una organización existente y permite crear duplicados.

## Propuesta

Cerrar el registro autoservicio. `POST /auth/register` solo podrá completar una invitación nominativa válida y verificada. No creará organizaciones.

Este change implementa el mecanismo de invitaciones y el registro protegido. El aprovisionamiento global de organizaciones y la emisión de la primera invitación para un `ADMIN` se implementarán en el change separado `add-platform-admin`. Durante las pruebas, la primera invitación podrá crearse mediante un seed o CLI controlado.

Una invitación emitida por un `ADMIN` de clínica solo podrá conceder `MEMBER`. La invitación inicial emitida por `PLATFORM_ADMIN` podrá conceder `ADMIN` y pertenece al change separado.

## Alcance

- Rechazar `/auth/register` sin token de invitación.
- Crear invitaciones nominativas de un solo uso, con expiración, hash y verificación de correo.
- Permitir a un `ADMIN` emitir invitaciones solo para su propia organización y solo con rol `MEMBER`.
- Aceptar invitaciones para crear la identidad y la membresía dentro de la organización indicada por el token.
- Migrar el rol persistido `USER` a `MEMBER`.
- Evitar enumeración de organizaciones y reutilización concurrente de invitaciones.

## Fuera de alcance

- Crear organizaciones desde el registro público.
- Crear o autenticar `PLATFORM_ADMIN`; pertenece a `add-platform-admin`.
- Aprovisionar la primera organización; pertenece a `add-platform-admin`.
- Fusionar organizaciones duplicadas existentes.

## Rutas

- `POST /auth/invitations/validate`: pública; valida el token sin crear datos.
- `POST /auth/register`: pública, pero exige token válido y verificación del correo.
- `POST /staff/invitations`: autenticada; solo `ADMIN` de la organización activa.

La emisión no vivirá bajo `/auth/**`, porque ese prefijo es público.

## Resultado

Un visitante que solo conoce la URL de la aplicación no podrá crear una organización, usuario ni membresía. Necesitará una invitación nominativa. El primer `ADMIN` se incorporará mediante la invitación creada por `add-platform-admin`.

## Pendientes del change separado

- Bootstrap y autenticación de `PLATFORM_ADMIN`.
- Endpoint `/platform/organizations`.
- Proveedor concreto de correo; este change usa la abstracción `InvitationMailer` y configuración SMTP.
- Crear el primer `PLATFORM_ADMIN` en cada entorno.

## Ajuste funcional: gestion de personal de la clinica

El registro publico no debe permitir crear administradores ni usuarios sin una
relacion previa con una organizacion. El primer `ADMIN` se incorpora mediante
la invitacion emitida por `add-platform-admin`.

Despues, el `ADMIN` de una clinica podra gestionar su personal desde la
aplicacion:

- Invitar usuarios de su propia organizacion.
- Invitar exclusivamente con el rol `MEMBER`.
- Consultar el listado, rol y estado de los miembros.
- Desactivar o reactivar miembros sin eliminar su historial clinico.
- Revocar invitaciones pendientes.

El registro con invitacion tomara la organizacion y el rol desde el token; el
usuario no podra modificarlos desde el formulario. El rol `ADMIN` quedara
reservado para el alta inicial controlada por plataforma. Por tanto, el
modelo de membresias que debe implementarse es `ADMIN | MEMBER`; el perfil
profesional y la habilitacion clinica no son roles de membresia, conforme a
`ADR-0016`.

