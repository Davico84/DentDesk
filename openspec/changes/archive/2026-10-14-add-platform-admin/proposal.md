# Cambio: Administración global de plataforma

## Problema

El registro pasará a exigir invitación, pero actualmente no existe una identidad autorizada para crear la primera organización ni emitir la primera invitación `ADMIN`.

Este change depende de `update-registration-organization-linking`: primero deben existir la tabla y el servicio de invitaciones; aquí se reutilizan para emitir la invitación inicial.

## Propuesta

Incorporar `PLATFORM_ADMIN` como autorización global separada de `Membership`. Un `PLATFORM_ADMIN` podrá crear organizaciones y emitir la invitación nominativa al primer `ADMIN`. No será una membresía de clínica ni dependerá de `organization_id`.

## Alcance

- Tabla `platform_admins` vinculada a `users`.
- Bootstrap controlado del primer `PLATFORM_ADMIN` mediante CLI/seed operativo, nunca endpoint público.
- `POST /platform/organizations` protegido.
- Emisión de la primera invitación con rol `ADMIN`.
- Autorización que impida a un `ADMIN` de clínica crear organizaciones.

## Fuera de alcance

- Panel completo de administración de plataforma.
- Gestión de usuarios globales fuera del bootstrap.
- Fusión de organizaciones existentes.
- Invitaciones `MEMBER`, que pertenecen a `update-registration-organization-linking`/`staff`.

## Orden de aplicación

1. Aplicar `update-registration-organization-linking`.
2. Aplicar `add-platform-admin`.
3. Verificar: plataforma crea clínica → invitación `ADMIN` → registro del `ADMIN` → invitación `MEMBER` → registro del `MEMBER`.

