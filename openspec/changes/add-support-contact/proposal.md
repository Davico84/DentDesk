# Proposal — add-support-contact

## Why

Segundo de los cuatro changes del despliegue (`docs/vision.md` §8): `add-cold-start-warmup` (hecho) → **`add-support-contact`** → `add-cloud-deployment` → `add-database-backups`.

Antes de publicar, quien no puede entrar (olvidó su contraseña, el servidor tarda, no sabe cómo registrarse) no tiene a quién escribir: la pantalla de espera larga dice "avisa al administrador" sin decir cómo, y el login y el registro no muestran ningún contacto. Con el horario 9–20 decidido para Render (`docs/deployment.md` §3), fuera de horario la espera de arranque en frío es lo esperable, y un contacto visible evita que la clínica piense que la app dejó de funcionar.

## What Changes

- **Contacto de soporte de Dentdesk** en una constante de `core/config` (`supportContact`): quién atiende ("David Velarde · Soporte Dentdesk", iniciales DV), en qué horario ("de 9:00 a 20:00", el mismo en que el servidor está despierto), WhatsApp `51959396384` y correo `davicova84@gmail.com` (decisión del usuario). Es el contacto **del producto**, no de cada clínica.
- **Tarjeta de soporte** (`SupportCard`, genérica en `core/components`): las iniciales, quién atiende y el horario, y dos botones de 44 px: **WhatsApp** (en otra pestaña, con el mensaje "Hola, necesito ayuda con Dentdesk") y **Correo** (`mailto:`). Una persona con horario, no una línea de enlaces: quien no puede entrar sabe a quién le escribe y cuándo le responden.
- **Dónde aparece** (opción B del diseño revisado con el usuario, lienzo "Contacto de soporte Dentdesk"):
  - **login y registro**: bajo el formulario, un botón discreto "¿Problemas para entrar? Habla con soporte" que despliega la tarjeta. Igual en escritorio y celular; el panel de marca no cambia;
  - **"Está tardando más de lo normal"**: la tarjeta visible directamente bajo "Reintentar", con "¿Sigue sin entrar? Escríbeme", en vez de "avisa al administrador".
- Accesible (botón con `aria-expanded`/`aria-controls`, aviso de que WhatsApp se abre en otra pestaña, objetivos de 44 px) y solo tokens de color.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `project-foundation`: nuevo requisito "Contacto de soporte en las pantallas de acceso".

## Non-goals

- Contacto dentro de la app con sesión (cabecera, Inicio): las pantallas sin sesión son donde falta.
- Formulario de contacto, chat, horario distinto por día o varios contactos.
- Contacto de soporte por clínica.
- Configurar el contacto sin tocar código (variables de entorno o `project.config.json` como en la plantilla de HSO): ver design D1.

## Impact

- Frontend: `core/config/supportContact.ts`, `core/components/SupportCard.tsx` y `core/components/SupportDisclosure.tsx` (nuevos), `AuthLayout`, `ServerWarmupScreen` y sus tests. Sin dependencias nuevas.
- Sin cambios de backend, contrato ni dominio.
- **Privacidad**: el nombre, el número y el correo quedan públicos en la web (los recogen bots de spam). Se recomienda a futuro un contacto de soporte propio de Dentdesk; cambiarlo es editar una constante.
- Docs: `docs/deployment.md` §7 y `docs/vision.md` §8.
