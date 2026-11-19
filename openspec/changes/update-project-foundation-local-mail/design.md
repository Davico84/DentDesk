# Design

## Context

- `SmtpSenders.create` fija siempre `mail.smtp.auth=true`, `mail.smtp.starttls.enable=true` y `mail.smtp.starttls.required=true`. Lo usan los dos adaptadores SMTP (`SmtpInvitationMailer`, `SmtpAccountMailer`).
- `MailProperties` es un record `@ConfigurationProperties("app.mail")`. Sus valores llegan de variables (`MAIL_*`) definidas en `application.yml`.
- Sin `MAIL_ENABLED=true` la invitación responde 503 (`add-platform-admin` D6) y los correos de la cuenta se descartan con un aviso en el log, sin destinatario ni token (`update-authentication-password-recovery` D4).
- Mailpit acepta SMTP sin TLS ni autenticación por defecto. Si el cliente intenta autenticarse sin TLS, lo rechaza.
- El backend local corre en la máquina (`./mvnw spring-boot:run`) y el PostgreSQL de `compose.yaml` publica su puerto en el host. Mailpit sigue el mismo patrón: el backend lo ve en `localhost:1025`.

## Goals / Non-Goals

**Goals:**
- Que `docker compose --env-file secrets.properties up -d` levante base y correo, y que el primer acceso local funcione sin cuenta SMTP.
- Que el modo inseguro no pueda llegar a un despliegue por un error de configuración.

**Non-Goals:**
- Soportar el modo local con el backend dentro de un contenedor (el host sería `mailpit`, no `localhost`). Hoy el backend local no corre en compose.

## Decisions

### D1. Un interruptor booleano explícito, no deducir el modo del host o del puerto

`app.mail.local-smtp` (`MAIL_LOCAL_SMTP`, por defecto `false`). Encendido, `SmtpSenders` fija `mail.smtp.auth=false` y no habilita STARTTLS. Apagado, todo queda como hoy.

- *Alternativa descartada:* relajar STARTTLS cuando el host es `localhost`. Es implícito y sorprende: un túnel SSH local hacia un SMTP real dejaría de cifrar sin que nadie lo pidiera.
- *Alternativa descartada:* certificado autofirmado en Mailpit y confiar en él desde Java (opción B). Agrega pasos para quien clona el repo y un `mail.smtp.ssl.trust` que también es peligroso si se cuela a producción.
- *Alternativa descartada:* dos propiedades (`starttls` y `auth` por separado). Nadie necesita una sin la otra, y cada combinación es un caso más que probar.

### D2. Salvaguarda en el arranque: modo local solo con host de loopback

**Política exacta.** Solo si `enabled=true` y `localSmtp=true`: el host se recorta (espacios alrededor) y se compara como texto, sin distinguir mayúsculas, contra exactamente `localhost`, `127.0.0.1` y `::1`. Cualquier otra forma se rechaza, también `[::1]`, `localhost.`, `127.0.0.2`, `0.0.0.0`, un nombre DNS o un host vacío. No se resuelve el DNS ni se interpreta la dirección: una regla corta y textual es más fácil de probar y no cambia según la red de la máquina. Con `enabled=false` no se valida el host (no se crea ningún conector SMTP).

**Mecanismo.** Un método de validación en el propio record `MailProperties`, invocado desde su constructor compacto (el binding de `@ConfigurationProperties` construye el record al arrancar, antes de crear cualquier bean que lo use). Si falla, el binding falla y el contexto no levanta: el deploy queda en rojo antes de atender pedidos. El mensaje nombra `MAIL_LOCAL_SMTP` y el host rechazado, nunca el usuario ni la contraseña. Mismo criterio que la validación de `JWT_SECRET`.

- *Alternativa descartada:* validar en `MailConfiguration` al crear los mailers. Depende de qué beans se crean según `enabled`, y un bean nuevo que use `MailProperties` podría saltársela.
- *Alternativa descartada:* validar en cada envío. El error aparecería con el primer correo, no al desplegar.

**Qué protege y qué no.** Protege contra un **error de configuración**: encender el modo local en un despliegue que apunta a un SMTP real. No es una frontera de seguridad contra quien controla la máquina: un túnel SSH o un relay en `localhost` hacia un SMTP real queda permitido, porque exige encender `MAIL_LOCAL_SMTP` a propósito. Se advierte en `docs/tooling-setup.md`.

### D3. Mailpit en el mismo `compose.yaml`, con imagen fijada

Servicio `mailpit` junto a `db` en `modules/backend/compose.yaml`, imagen `axllent/mailpit` con versión exacta (no `latest`), publicado solo en `127.0.0.1`. Sin volumen: los correos de prueba no necesitan persistir.

- Puertos publicados con variables **propias de compose**: `MAILPIT_SMTP_PORT` (por defecto `1025`) y `MAILPIT_WEB_PORT` (por defecto `8025`). No se reutiliza `MAIL_PORT`: ese es el puerto al que conecta el backend (en producción, 2525 de Brevo) y mezclarlo movería Mailpit sin querer. En el caso local estándar `MAIL_PORT=1025` y `MAILPIT_SMTP_PORT=1025` coinciden; si se cambia uno, se cambia el otro.

- Un solo `docker compose ... up -d` levanta todo; quien no use correo no pierde nada (la app no lo usa sin `MAIL_ENABLED=true`).

### D4. Configuración local lista para descomentar

`secrets.properties.example` suma un bloque comentado: `MAIL_ENABLED=true`, `MAIL_LOCAL_SMTP=true`, `MAIL_HOST=localhost`, `MAIL_PORT=1025`, `MAIL_FROM=no-reply@dentdesk.local`. Comentado, para que copiar el archivo tal cual no cambie el comportamiento actual.

## Risks / Trade-offs

- [Alguien enciende `MAIL_LOCAL_SMTP` en Render] → D2 impide el arranque porque el host de Brevo no es loopback; el deploy falla visible y producción sigue con la versión anterior.
- [Agregar un campo al record `MailProperties` rompe los `new MailProperties(...)` de los tests] → se actualizan en la misma tarea; es compilación, no comportamiento.
- [Puertos 1025/8025 ocupados en la máquina] → `MAILPIT_SMTP_PORT`/`MAILPIT_WEB_PORT`, igual que `DB_PORT`, documentados.
- [Un relay o túnel en `localhost` hacia un SMTP real con el modo local encendido] → aceptado (ver D2, "Qué protege y qué no"); advertencia en `docs/tooling-setup.md`.
- [Test con servidor SMTP falso por socket inestable en CI] → protocolo determinista, timeouts cortos y cierre garantizado (tarea 1.4).

## Migration Plan

Sin migración. En producción no se define `MAIL_LOCAL_SMTP` (queda `false`). Rollback: revertir el commit; la variable sin usar no afecta.
