# Proposal — add-support-contact

## Why

Segundo de los cuatro changes del despliegue (`docs/vision.md` §8): `add-cold-start-warmup` (hecho) → **`add-support-contact`** → `add-cloud-deployment` → `add-database-backups`.

Antes de publicar, quien no puede entrar (olvidó su contraseña, el servidor tarda, no sabe cómo registrarse) no tiene a quién escribir: la pantalla de espera larga dice "avisa al administrador" sin decir cómo, y el login y el registro no muestran ningún contacto. Con el horario 9–20 decidido para Render (`docs/deployment.md` §3), fuera de horario la espera de arranque en frío es lo esperable, y un contacto visible evita que la clínica piense que la app dejó de funcionar.

## What Changes

- **Contacto de soporte de Dentdesk** en una constante de `core/config` (`supportContact`): WhatsApp `51959396384` y correo `davicova84@gmail.com` (decisión del usuario, los mismos que HSO). Es el contacto **del producto**, no de cada clínica, así que no va por organización ni por base de datos.
- **`ContactLinks`** (componente genérico en `core/components`): "¿Necesitas ayuda?" con dos enlaces:
  - "Escríbenos por WhatsApp", que abre `wa.me` en otra pestaña con un mensaje inicial ("Hola, necesito ayuda con Dentdesk");
  - el correo (`mailto:`).
- **Dónde aparece**:
  - en el **panel de marca** (`BrandPanel`) en escritorio: login, registro y pantalla de arranque en frío;
  - **debajo del formulario** de login y registro en celular, donde el panel no se ve;
  - en la pantalla de espera, estado **"Está tardando más de lo normal"**: "Si el problema continúa, escríbenos:" con los enlaces, en vez de "avisa al administrador".
- Accesible (enlaces con nombre claro, aviso de que WhatsApp se abre en otra pestaña, foco visible sobre el panel de marca) y solo tokens de color.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `project-foundation`: nuevo requisito "Contacto de soporte en las pantallas de acceso".

## Non-goals

- Contacto dentro de la app con sesión (cabecera, Inicio): las pantallas sin sesión son donde falta.
- Formulario de contacto, chat, horarios de atención o varios contactos.
- Contacto de soporte por clínica.
- Configurar el contacto sin tocar código (variables de entorno o `project.config.json` como en la plantilla de HSO): ver design D1.

## Impact

- Frontend: `core/config/supportContact.ts` (nuevo), `core/components/ContactLinks.tsx` (nuevo), `BrandPanel`, `AuthLayout`, `ServerWarmupScreen` y sus tests. Sin dependencias nuevas.
- Sin cambios de backend, contrato ni dominio.
- **Privacidad**: el número y el correo quedan públicos en la web (los recogen bots de spam). Se recomienda a futuro un contacto de soporte propio de Dentdesk; cambiarlo es editar una constante.
- Docs: `docs/deployment.md` §7 y `docs/vision.md` §8.
