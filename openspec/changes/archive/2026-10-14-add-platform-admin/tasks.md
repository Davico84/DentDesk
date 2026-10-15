## 1. Modelo y bootstrap

- [x] 1.0 Verificar que `update-registration-organization-linking` esté aplicado y que el servicio de invitaciones esté disponible.

- [x] 1.1 Crear migración `platform_admins`.
- [x] 1.2 Crear entidad/repositorio y guard de autorización.
- [x] 1.3 Implementar CLI/seed de bootstrap y documentar su uso por entorno. (El runner fallaba con `null identifier` al guardar `PlatformAdmin`; corregido con `@MapsId`. Cubierto por `PlatformAdminBootstrapRunnerTest` y `PlatformAdminIT`; procedimiento en design.md.)
- [x] 1.4 Definir sesión/claim para rutas globales sin mezclar el tenant.

## 2. Aprovisionamiento

- [x] 2.1 Implementar `POST /platform/organizations` protegido.
- [x] 2.2 Crear organización + settings + invitación inicial `ADMIN` transaccionalmente.
- [x] 2.3 Impedir acceso de `ADMIN` de clínica y visitantes.
- [x] 2.4 Entregar el token solo una vez y delegar su correo al `InvitationMailer`. (Envío tras flush dentro de la transacción, D6; sin correo configurado se rechaza, D7.)

## 3. Contrato y pruebas

- [x] 3.1 Documentar DTOs y errores RFC 9457 en OpenAPI. (`contracts/openapi.json` y cliente regenerados; `operationId` únicos.)
- [x] 3.2 Probar que solo `PLATFORM_ADMIN` puede aprovisionar.
- [x] 3.3 Probar que falla la operación si la creación de organización o invitación falla.
- [x] 3.4 Probar el flujo completo plataforma → invitación ADMIN → registro. (Automatizado con mailer simulado, hasta el registro del `MEMBER`. Prueba manual con correo real OK el 2026-10-14: bootstrap → login de plataforma → clínica → correo → verificación → registro del `ADMIN`.)
- [x] 3.5 Ejecutar `openspec validate add-platform-admin --strict` y `mvn verify`. (2026-10-14: ambos en verde, tras cerrar la 6.5 de `update-registration-organization-linking`.)

