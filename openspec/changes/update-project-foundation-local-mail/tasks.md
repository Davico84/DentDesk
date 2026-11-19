# Tasks

## 1. Backend: modo local y salvaguarda

- [ ] 1.1 Agregar `localSmtp` a `MailProperties` y `local-smtp: ${MAIL_LOCAL_SMTP:false}` en `application.yml`; actualizar los `new MailProperties(...)` existentes en tests. Verifica: `./mvnw test-compile`.
- [ ] 1.2 `SmtpSenders`: con `localSmtp=false` mantener auth y STARTTLS requeridos; con `true`, `mail.smtp.auth=false` sin STARTTLS (design D1). Verifica: test unitario que inspecciona las `JavaMailProperties` en los dos modos (Scenario "Configuración por defecto").
- [ ] 1.3 Salvaguarda de arranque (design D2): con correo habilitado y `localSmtp=true`, rechazar un host que no sea `localhost`/`127.0.0.1`/`::1`, con un mensaje que nombre `MAIL_LOCAL_SMTP` y el host, sin usuario ni contraseña. Verifica: tests de los Scenarios "Modo local con un host remoto" (incluye que el mensaje no contiene la contraseña) y "Modo local con el correo deshabilitado".
- [ ] 1.4 Test con un servidor SMTP falso en un socket local que no ofrece STARTTLS: en modo normal el envío falla con `InvitationDeliveryException`; en modo local la conversación llega a `DATA` sin `STARTTLS` ni `AUTH`. Verifica: Scenarios "Servidor sin STARTTLS en modo normal" y "Modo local contra un servidor de prueba local".

## 2. Entorno local

- [ ] 2.1 Servicio `mailpit` en `modules/backend/compose.yaml` con versión exacta de `axllent/mailpit` (verificada en Docker Hub, no `latest`), puertos `127.0.0.1:1025` y `127.0.0.1:8025` configurables por variable (design D3). Verifica: `docker compose --env-file secrets.properties up -d` y `http://localhost:8025` responde.
- [ ] 2.2 Bloque comentado de correo local en `secrets.properties.example` (design D4). Verifica: copiar el `.example` sin descomentar deja `MAIL_ENABLED` sin definir.
- [ ] 2.3 Prueba manual de punta a punta en local: administrador de plataforma, clínica e invitación (`docs/deployment.md` §11) con Mailpit; la invitación aparece en `http://localhost:8025`, el registro con su código funciona, y la recuperación de contraseña también llega. Verifica: el user confirma.

## 3. Documentación

- [ ] 3.1 `docs/tooling-setup.md`: sección "Correo local con Mailpit" (levantar, configurar, ver la bandeja, por qué el modo local no arranca con un host remoto). Verifica: revisión.
- [ ] 3.2 `readme.md`, paso "Primer acceso": Mailpit en vez de "requiere SMTP configurado". Depende de que el PR del README (`docs/readme-portafolio`) esté en `dev`; si no, se hace al traerlo. Verifica: el enlace a `docs/tooling-setup.md` existe.

## 4. Cierre

- [ ] 4.1 `./mvnw verify` en verde (como en CI) y `openspec validate update-project-foundation-local-mail --strict`. Verifica: salida de ambos comandos.

## Workflow follow-up

- Prompt de revisión para Codex en el chat antes del PR; pedir permiso antes de abrir el PR.
- `/opsx:archive` al terminar, actualizando `docs/vision.md`.
