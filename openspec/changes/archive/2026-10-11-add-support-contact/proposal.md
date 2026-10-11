# Proposal — add-support-contact

## Why

Segundo de los cuatro changes del despliegue (`docs/vision.md` §8): `add-cold-start-warmup` (hecho) → **`add-support-contact`** → `add-cloud-deployment` → `add-database-backups`.

Antes de publicar, quien no puede entrar (olvidó su contraseña, el servidor tarda, no sabe cómo registrarse) no tiene a quién escribir: la pantalla de espera larga dice "avisa al administrador" sin decir cómo, y el login y el registro no muestran ningún contacto. Con el horario 9–20 decidido para Render (`docs/deployment.md` §3), fuera de horario la espera de arranque en frío es lo esperable, y un contacto visible evita que la clínica piense que la app dejó de funcionar.

## What Changes

- **Contacto de soporte de Dentdesk** en una constante de `core/config` (`supportContact`): WhatsApp `51959396384` y correo `davicova84@gmail.com` (decisión del usuario). Es el contacto **del producto**, no de cada clínica.
- **Fila de soporte** (`SupportRow`, genérica en `core/components`; opción **C2 · Fila compacta** del diseño revisado con el usuario, lienzo "Contacto de soporte Dentdesk"): una píldora con la pregunta "¿Problemas para entrar?" y dos botones redondos de 44 px y solo ícono, **WhatsApp** (en otra pestaña, con el mensaje "Hola, necesito ayuda con Dentdesk") y **Correo** (`mailto:`), cada uno con nombre accesible y tooltip. Ocupa ~60 px.
- **Dónde aparece**:
  - **login y registro**: bajo el formulario, siempre a la vista, en escritorio y celular; el panel de marca no cambia;
  - **"Está tardando más de lo normal"**: bajo "Reintentar", con "¿Sigue sin entrar?", en vez de "avisa al administrador".
- Primero se implementó la opción B (botón que despliega una tarjeta con persona y horario); el usuario la encontró con demasiada información y se reemplazó por la C2.

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

- Frontend: `core/config/supportContact.ts` y `core/components/SupportRow.tsx` (nuevos), `AuthLayout`, `ServerWarmupScreen` y sus tests. Sin dependencias nuevas.
- Sin cambios de backend, contrato ni dominio.
- **Privacidad**: el número y el correo quedan públicos en la web (los recogen bots de spam). Se recomienda a futuro un contacto de soporte propio de Dentdesk; cambiarlo es editar una constante.
- Docs: `docs/deployment.md` §7 y `docs/vision.md` §8.
