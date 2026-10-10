## Why

Después de iniciar sesión no hay **marco de aplicación**: `HomeScreen` es un placeholder (logo, saludo y dos botones) y cada pantalla se navega solo con enlaces de "volver". No hay barra lateral, ni cabecera con el usuario y la clínica, ni forma visible de ir de una sección a otra; cerrar sesión solo está en el inicio. `docs/frontend.md` §4.3 ya lo marca como objetivo no adoptado ("la Sidebar del `AppLayout` no existe todavía"). Con más capacidades en camino (citas, tratamientos, cobros), sin un shell cada una tendría que inventar su navegación.

Es el tercer ítem del checklist de mejoras traídas del proyecto hermano HSO (`docs/vision.md` §8), que lo construyó en cuatro changes (`add-app-shell`, `update-app-shell-tooltips`, `update-app-shell-role-nav`, `update-app-shell-collapsible-sidebar`) más el patrón de "volver al listado" de `update-orthodontic-records-back-link`. Acá entra en uno solo, ya con Tailwind 4 (`update-tailwind-v4`), que destraba los componentes de shadcn que hacen falta.

## What Changes

- **Nueva capacidad `app-shell`** (técnica/plataforma, solo frontend):
  - **Layout privado** (`layouts/AppLayout`) que envuelve todas las rutas autenticadas: **barra lateral** con el logo de la organización y la navegación, **cabecera** con el usuario, su rol, la clínica activa y el conmutador de tema, y **cerrar sesión** al pie de la barra. Login y registro siguen sin shell.
  - **Secciones declaradas en una sola configuración** (`core/config/sections.ts`): de ella salen el menú y el árbol de rutas, con los roles que puede verlas cada una. Solo lo que existe hoy (decisión del usuario): **Inicio**, **Pacientes** y **Configuración › Marca** (solo `ADMIN`). Cada capacidad nueva agrega su sección al construirse. Un usuario sin permiso que llega por URL ve "Acceso denegado" **dentro** del shell.
  - **Responsive**: barra expandida en escritorio (≥ 1024 px), solo íconos con tooltip en tablet, y cajón con botón de menú en celular.
  - **Barra contraíble en escritorio**, con la preferencia del usuario recordada en el navegador.
  - **Ancho del marco**: en pantallas muy anchas la app mide como máximo 1920 px y va centrada. El ancho del contenido de cada pantalla sigue siendo el de `PageContainer` (uniforme, decidido en `update-clinical-history`).
  - **Inicio con bienvenida y atajos reales** (decisión del usuario): saludo con el nombre y la clínica, y accesos a Nuevo paciente, Buscar paciente y, para el `ADMIN`, Marca. Sin datos de ejemplo: las métricas son la capacidad `dashboard`.
- **`patients`**: el listado guarda la búsqueda y la página en la URL, y "volver al listado" desde la ficha o el formulario regresa a esa misma búsqueda y página.
- Se agregan los componentes `sheet` (cajón) de shadcn a `core/ui`.
- **Docs**: `frontend.md` §1 y §4.3 (el `AppLayout` existe), `docs/vision.md` §8.

## Capabilities

### New Capabilities

- `app-shell`: marco de la zona privada: layout con barra lateral y cabecera, navegación por configuración con roles, responsive, barra contraíble, ancho del marco e Inicio.

### Modified Capabilities

- `patients`: el listado conserva la búsqueda y la página al volver desde la ficha.

`authentication` no cambia de comportamiento: su scenario *Rol sin permiso* (estado de acceso denegado con opción de volver) se sigue cumpliendo, ahora dentro del shell.

## Non-goals

- **Métricas del Inicio**: son la capacidad `dashboard`.
- **Secciones "Próximamente"** para capacidades que no existen (decisión del usuario).
- **Cambiar de organización activa** desde la cabecera (`update-multi-tenancy`, roadmap).
- **Editar el perfil** del usuario desde la cabecera (`update-authentication`).
- **Navegación interna del paciente** (pestañas Historia / Consultas / Odontograma): sigue en la ficha.
- **Búsqueda global** en la cabecera.
- **Autorización real**: ocultar un ítem o proteger una ruta del frontend es UX; los endpoints siguen con su `@PreAuthorize`.

## Impact

- **Frontend**: `layouts/AppLayout` (nuevo), `core/config/sections.ts` (nuevo), `routes/index.tsx` (rutas privadas bajo el shell, derivadas de las secciones), `core/auth/AccessDenied` (variante dentro del shell), `core/components/PageContainer` (deja de ser `<main>` y de ocupar el alto de pantalla: eso lo da el shell), `screens/home/HomeScreen` (Inicio), `modules/patients` (búsqueda y página en la URL, volver al listado), `core/ui/sheet` (nuevo, de shadcn).
- **Tests**: Vitest/MSW por scenario; e2e de navegación responsive a 375, 768 y 1280 px sin desplazamiento horizontal.
- Sin cambios de backend ni de contrato.
