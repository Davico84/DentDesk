# Proposal

## Why

Desde que el alta es solo por invitación, nadie puede entrar a una instalación local sin un servidor SMTP real: sin correo configurado la invitación se rechaza con 503, y el conector SMTP exige STARTTLS y autenticación, que un servidor de correo de prueba local no ofrece de fábrica. Con el repo público, quien lo clona no puede probar la aplicación sin crear su propia cuenta en un proveedor de correo.

## What Changes

- Servicio **Mailpit** (servidor SMTP de prueba con bandeja web) en `modules/backend/compose.yaml`, con imagen fijada, SMTP en el puerto 1025 y bandeja en `http://localhost:8025`.
- Un interruptor de configuración nuevo, `MAIL_LOCAL_SMTP`, apagado por defecto: encendido, el conector SMTP no exige STARTTLS ni autenticación, para hablar con Mailpit.
- **Salvaguarda:** con el interruptor encendido el backend solo arranca si el host SMTP es local (`localhost`, `127.0.0.1` o `::1`). Así un despliegue no puede quedar enviando correo sin cifrar por error.
- Por defecto, sin el interruptor, el comportamiento no cambia: STARTTLS y autenticación exigidos (producción con Brevo igual que hoy).
- `secrets.properties.example`: bloque comentado con la configuración local (Mailpit) lista para descomentar.
- Documentación: `docs/tooling-setup.md`, sección nueva de correo local, con el paso a paso desde un clon limpio.

## Capabilities

### New Capabilities

_Ninguna._

### Modified Capabilities

- `project-foundation`: requirement nuevo sobre el envío de correo seguro por defecto y el modo local explícito y acotado a la máquina.

## Non-goals

- No cambia el proveedor ni la configuración de producción (Brevo, puerto 2525 en Render).
- No agrega un mailer que escriba los enlaces en el log: el token de invitación y de recuperación nunca va al log (`update-authentication-password-recovery` design D4, `add-platform-admin` D6).
- No automatiza el alta del primer usuario local (administrador de plataforma, clínica e invitación siguen el procedimiento de `docs/deployment.md` §11). Solo lo hace posible sin cuenta SMTP.
- No se usa Mailpit en los tests automatizados: los tests siguen sustituyendo los mailers.
- No toca el `readme.md`: su reestructuración va en otro PR (`docs/readme-portafolio`). Cuando ambos estén en `dev`, el paso "Primer acceso" del readme enlaza a la sección nueva de `docs/tooling-setup.md` en un cambio de documentación aparte.

## Impact

- Backend: `infra/mail` (`MailProperties`, `SmtpSenders`, `MailConfiguration`), `application.yml`, tests unitarios del conector.
- Configuración local: `modules/backend/compose.yaml`, `modules/backend/secrets.properties.example`.
- Docs: `docs/tooling-setup.md`. Sin cambios de API, contrato OpenAPI, base de datos ni frontend.
