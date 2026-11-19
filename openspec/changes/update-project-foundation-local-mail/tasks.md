# Tasks

## 1. Backend: modo local y salvaguarda

- [ ] 1.1 Agregar `localSmtp` a `MailProperties` y `local-smtp: ${MAIL_LOCAL_SMTP:false}` en `application.yml`; actualizar los `new MailProperties(...)` existentes en tests. Verifica: `./mvnw test-compile`.
- [ ] 1.2 `SmtpSenders`: con `localSmtp=false` mantener `mail.smtp.auth=true`, `mail.smtp.starttls.enable=true` y `mail.smtp.starttls.required=true`; con `true`, `mail.smtp.auth=false` y sin STARTTLS (design D1). Verifica: test unitario que inspecciona las tres propiedades en los dos modos (Scenario "Configuración por defecto").
- [ ] 1.3 Salvaguarda de host en el constructor compacto de `MailProperties` (design D2, política exacta): solo con `enabled && localSmtp`; recorta espacios y compara sin mayúsculas contra `localhost`, `127.0.0.1`, `::1`. Verifica: tests parametrizados de los Scenarios "Hosts locales aceptados" (`localhost`, `LOCALHOST`, `127.0.0.1`, `::1`, `" localhost "`), "Host remoto o ambiguo rechazado" (`smtp-relay.brevo.com`, `localhost.`, `127.0.0.2`, `[::1]`, `0.0.0.0`, vacío), "Error del arranque rechazado sin secretos" (el mensaje contiene `MAIL_LOCAL_SMTP` y el host, y no el usuario ni la contraseña) y "Modo local con el correo deshabilitado" (host remoto con `enabled=false` no lanza).
- [ ] 1.4 Servidor SMTP falso para tests (helper en `src/test`): `ServerSocket` en puerto dinámico, un hilo daemon que atiende una sola conexión con protocolo determinista (`220`; `EHLO` responde capacidades configurables, con o sin `STARTTLS`; `MAIL FROM`/`RCPT TO`/`DATA`/`.`/`QUIT` con `250`/`354`/`221`), `soTimeout` de 2 s en el socket, registro de los comandos recibidos, `join` acotado y cierre en `finally`. Timeout del mailer en el test: 2 s. Verifica: el propio helper se usa en 1.5 y 1.6 sin colgarse (`./mvnw test` repetido 3 veces en verde).
- [ ] 1.5 Test modo normal contra el servidor falso **sin** `STARTTLS`: `SmtpInvitationMailer.send` lanza `InvitationDeliveryException` y los comandos registrados no incluyen `DATA`. Verifica: Scenario "Servidor sin STARTTLS en modo normal".
- [ ] 1.6 Tests modo local contra el servidor falso: `SmtpInvitationMailer` y `SmtpAccountMailer` (enlace de recuperación) entregan, los comandos registrados incluyen `DATA` y no incluyen `STARTTLS` ni `AUTH`. Verifica: Scenarios "Invitación en modo local" y "Recuperación de contraseña en modo local".

## 2. Entorno local

- [ ] 2.1 Servicio `mailpit` en `modules/backend/compose.yaml`: versión exacta de `axllent/mailpit` verificada en Docker Hub (no `latest`), puertos `127.0.0.1:${MAILPIT_SMTP_PORT:-1025}:1025` y `127.0.0.1:${MAILPIT_WEB_PORT:-8025}:8025` (design D3). Verifica: desde `modules/backend`, con un `secrets.properties` copiado del `.example` y `DB_*` completos, `docker compose --env-file secrets.properties up -d` levanta `db` y `mailpit`, y `http://localhost:8025` responde.
- [ ] 2.2 Bloque comentado de correo local en `secrets.properties.example` (design D4): `MAIL_ENABLED`, `MAIL_LOCAL_SMTP`, `MAIL_HOST=localhost`, `MAIL_PORT=1025`, `MAIL_FROM`, y `MAILPIT_SMTP_PORT`/`MAILPIT_WEB_PORT` explicando que `MAIL_PORT` y `MAILPIT_SMTP_PORT` van coordinados. Verifica: copiar el `.example` sin descomentar deja `MAIL_ENABLED` y `MAIL_LOCAL_SMTP` sin definir.
- [ ] 2.3 Prueba manual de punta a punta en local desde un clon limpio: copiar el `.example`, completar `DB_*` y `JWT_SECRET`, descomentar el bloque local, `docker compose ... up -d`, arrancar backend y frontend, crear el administrador de plataforma, dar de alta una clínica (`docs/deployment.md` §11), ver la invitación en `http://localhost:8025`, registrarse con su código, y pedir la recuperación de contraseña y verla llegar. Verifica: el user confirma.

## 3. Documentación

- [ ] 3.1 `docs/tooling-setup.md`: sección "Correo local con Mailpit" con el paso a paso de 2.3, la relación entre `MAIL_PORT` y `MAILPIT_SMTP_PORT`, por qué el modo local no arranca con un host remoto, y la advertencia de que no protege contra un túnel o relay en `localhost` hacia un SMTP real (design D2). Verifica: revisión.

## 4. Cierre

- [ ] 4.1 `./mvnw verify` en verde (como en CI) y `openspec validate update-project-foundation-local-mail --strict`. Verifica: salida de ambos comandos.

## Workflow follow-up

- Prompt de revisión para Codex en el chat antes del PR; pedir permiso antes de abrir el PR.
- `/opsx:archive` al terminar, actualizando `docs/vision.md`.
- Cuando este change y el PR del readme (`docs/readme-portafolio`) estén en `dev`: enlazar desde el paso "Primer acceso" del readme a la sección de `docs/tooling-setup.md` (cambio de documentación aparte).
