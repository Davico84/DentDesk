# Proposal

## Why

El login muestra "¿Olvidaste tu contraseña?", pero el enlace no hace nada: no hay backend. Quien olvida la contraseña
hoy no tiene forma de volver a entrar sin que alguien con acceso a la base la cambie a mano. Con clínicas reales
usando Dentdesk, una cuenta perdida deja a un profesional sin poder atender ni firmar.

Además, `update-authentication-my-profile` (recién construida) permite cambiar la contraseña, pero el dueño de la
cuenta no se entera si otra persona lo hizo. Un aviso por correo es la alarma que le falta.

## What Changes

- **Pedir la recuperación** desde el login: se indica el correo y la respuesta es **siempre la misma**, exista o no
  la cuenta, para no revelar quién está registrado.
- **Enlace por correo**: vale **30 minutos**, se usa **una sola vez**, y pedir otro anula el anterior. Se envían como
  máximo **3 por cuenta en 24 horas**; pasado el límite, la respuesta sigue siendo la misma, sin correo.
- **Elegir la contraseña nueva** desde el enlace, con la regla del registro (mínimo 8 caracteres). Al guardarla:
  - se cierran **todas** las sesiones del usuario, también la del dispositivo desde el que se recuperó;
  - se vuelve al login.
- **La recuperación no desbloquea la cuenta.** Si al guardar la cuenta está bloqueada por intentos fallidos, se avisa
  hasta cuándo; si el usuario o su acceso a la clínica están desactivados, se avisa que debe contactar al
  administrador. El aviso aparece **después** de abrir el enlace, cuando la persona ya demostró que el correo es suyo.
- **Los administradores de plataforma quedan fuera**: piden igual que cualquiera y reciben la misma respuesta, pero no
  se les envía nada. Su cuenta es la de mayor privilegio y no tiene segundo factor: que la casilla de correo baste
  para tomarla no es aceptable. Recuperan por un procedimiento operativo documentado.
- **Aviso "tu contraseña fue cambiada"** por correo, tanto al recuperarla como al cambiarla desde Mi perfil.

## Non-goals

- **Segundo factor (2FA)** ni recuperación por otro canal (SMS, preguntas de seguridad).
- **Límite por IP**: requiere Redis (`docs/architecture.md`), fuera de alcance; el límite es por cuenta.
- **Desbloquear la cuenta** al recuperar la contraseña, o que un administrador desbloquee: el bloqueo vence solo.
- **Recuperación del administrador de plataforma desde la aplicación**: se documenta el procedimiento operativo.
- **Cambiar el correo** de la cuenta.
- **Reglas de contraseña más fuertes** que las del registro.
- **Plantillas de correo con la marca de cada clínica**: el correo lo firma Dentdesk.

## Capabilities

### New Capabilities

_Ninguna._

### Modified Capabilities

- `authentication`: se agregan pedir la recuperación de la contraseña, restablecerla con el enlace (cerrando todas
  las sesiones) y el aviso por correo de cualquier cambio de contraseña. El aviso se agrega como requirement propio:
  el de "Cambiar la contraseña propia" no cambia de texto.

## Impact

- **Backend**: tabla nueva para los enlaces de recuperación (hash del token, vencimiento, uso); dos endpoints públicos
  bajo `/auth` que se suman a la lista explícita de `PublicPaths`; envío de correo **fuera de la petición** con un
  executor propio (el proyecto prohíbe `@Async` por el contexto de tenant), solo con datos ya calculados; revocación
  de los refresh tokens y el protocolo de bloqueo de fila de Mi perfil. Migración Flyway nueva.
- **Correo**: dos correos nuevos (enlace de recuperación y aviso de cambio) sobre el SMTP existente (Brevo).
- **Contrato**: `contracts/openapi.json` y el cliente orval regenerados.
- **Frontend**: pantallas "¿Olvidaste tu contraseña?" y "Nueva contraseña", el enlace del login conectado, y el aviso
  de bloqueo o de cuenta desactivada en el login tras el cambio.
- **Docs**: `docs/vision.md` (estado), `docs/deployment.md` (procedimiento para el administrador de plataforma),
  `docs/domain.md` si suma el enlace de recuperación al glosario.
