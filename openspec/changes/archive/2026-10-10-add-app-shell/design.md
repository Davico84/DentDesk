# Design — add-app-shell

## Context

- **Routing hoy** (`routes/index.tsx`): `RootLayout` (restaura la sesión, aplica la marca, monta el `Toaster`) envuelve `RequireGuest` (login, registro) y `RequireAuth` (rutas privadas, con `roles` opcionales). La única ruta con rol es `BRANDING` (`RequireAuth roles={["ADMIN"]}`).
- **Cada pantalla trae su propio `<main>`** vía `core/components/PageContainer` (`mx-auto min-h-screen max-w-5xl p-6`), creado en `update-clinical-history` para unificar el ancho. Dentro del shell eso anidaría `<main>`.
- `AccessDenied` es pantalla completa con `<main>` propio: dentro del shell también anidaría.
- `HomeScreen` es un placeholder: logo de la organización, saludo, enlaces a Pacientes y Marca (ADMIN), cerrar sesión.
- El listado de pacientes guarda búsqueda, página e "incluir dados de baja" en `useState`: al volver de la ficha se pierden.
- Ya existen: `core/ui/tooltip` (Radix), `core/ui/theme-toggle`, `branding/components/OrgLogo`, `core/ui/logo` (logo del producto), `useLogout`, `useSessionStore` (con `user.fullName`, `role`, `organizationName`). Tailwind 4 ya migrado (`update-tailwind-v4`): los componentes de shadcn llegan del registro sin adaptar.
- Antecedente: HSO lo resolvió en cuatro changes (shell, tooltips, roles, barra contraíble). Se reutiliza su modelo (`sections.ts`, `AppLayout`, componentes en `core/components/shell`, store de la barra), con dos diferencias: cajón con `sheet` de shadcn (HSO lo hizo a mano) y ancho de contenido por `PageContainer`.

## Goals / Non-Goals

**Goals:** un marco único para la zona privada; secciones y roles en un solo lugar; navegable en celular, tablet y escritorio; sin `<main>` anidados; volver al listado sin perder la búsqueda.

**Non-Goals:** los del proposal (métricas, "Próximamente", cambio de organización, perfil, pestañas del paciente, búsqueda global).

## Decisions

### D1. Secciones en una sola configuración neutral

`core/config/sections.ts` declara cada sección como `{ id, path, roles? }` y exporta `canAccess(section, role)`. Hoy: `home` (`/`), `patients` (`/patients`, con todas sus subrutas), `branding` (`/settings/branding`, `["ADMIN"]`).

- El **menú** (`core/components/shell/navItems.ts`) agrega solo etiqueta e ícono por `id`.
- El **árbol de rutas** (`routes/index.tsx`) agrupa las rutas de cada sección bajo un `RequireAuth roles={section.roles}`: los roles no se escriben en otro sitio.
- `core` no depende de otros módulos: la configuración usa solo `PATHS` y el tipo `Role` del store.

*Alternativa descartada:* roles en cada `<Route>` y el menú filtrado aparte, que es como estaba: dos fuentes que se desalinean en cuanto llega la primera sección nueva.

### D2. Shell como layout anidado bajo `RequireAuth`

```
RootLayout (sesión, marca, toasts)
├─ RequireGuest → login, registro          (sin shell)
└─ RequireAuth
   └─ AppLayout (barra + cabecera + <main>)
      ├─ home, patients/*                    (sin roles)
      └─ RequireAuth roles=[ADMIN] → branding
```

`AppLayout` vive en `layouts/` porque conecta `modules/auth` (`useLogout`), `modules/branding` (`OrgLogo`) y el store con los componentes de `core/components/shell` (`Sidebar`, `Header`, `NavItem`, `MobileNav`), que no pueden depender de otros módulos (`docs/frontend.md` §1). El logo entra a `Sidebar` como prop (`logo: ReactNode`).

### D3. Un solo `<main>`: `PageContainer` deja de ser `<main>`

`AppLayout` pone el único `<main>`. `PageContainer` pasa a ser un `<div>` sin `min-h-screen` que conserva el ancho uniforme (`max-w-5xl`, centrado) decidido en `update-clinical-history`. El ancho de **contenido** lo sigue dando `PageContainer`; el shell solo limita el **marco** (D6).

`AccessDenied` gana una variante `inShell`: sin `<main>` ni alto de pantalla, con el foco en su título al montar (scenario *Acceso denegado dentro del shell*). Fuera del shell queda como está.

*Alternativa descartada:* el ancho de contenido de HSO (`max-w-screen-2xl`, 1536 px). Contradice el ancho uniforme que pidió el usuario y estiraría los formularios.

### D4. Responsive con el cajón de shadcn

| Ancho | Barra | Cabecera |
|---|---|---|
| < 640 px (celular) | oculta; botón de menú abre un `Sheet` lateral | botón de menú |
| 640–1023 px (tablet) | fija, solo íconos (64 px) con tooltip | — |
| ≥ 1024 px (escritorio) | expandida (240 px) o contraída (64 px), según preferencia | — |

- **Cajón**: `core/ui/sheet` (shadcn, sobre Radix Dialog) da foco atrapado, `Escape`, clic en el fondo y fondo inerte sin código propio. Se cierra al navegar (efecto sobre `location.pathname`). Sin tooltips dentro: las etiquetas están visibles.
- **Tooltips**: el `HintTooltip`/`Tooltip` existente, solo cuando la barra muestra íconos. El ítem conserva `aria-label`; sin atributo `title`. El nombre se anuncia una sola vez (el contenido del tooltip es `aria-hidden` cuando el disparador ya tiene `aria-label`).
- **Ítem activo**: `NavLink` con `end` solo para `/` (Inicio); Pacientes queda activo en `/patients/*`.

*Alternativa descartada:* cajón propio como en HSO: sin foco atrapado, el teclado se escapa al contenido de atrás.

### D5. Preferencia de la barra contraída

Store de Zustand `useSidebarStore` (`collapsed`, `toggle`) con `persist` en `localStorage` bajo una clave propia. Si el almacenamiento falla (modo privado, cuota), `persist` cae a memoria y la barra funciona igual durante la visita. Sin reglas por ruta ni por ancho: solo cambia con el botón, que se muestra desde 1024 px y refleja el estado con `aria-expanded`.

### D6. Ancho del marco

`AppLayout` envuelve todo en un marco `max-w-[1920px] mx-auto` sobre un fondo `bg-muted/40`; la barra es `sticky top-0 h-screen` dentro del marco, así viaja con el contenido en pantallas muy anchas. Hasta 1920 px no cambia nada visible.

### D7. Inicio

`screens/home/HomeScreen` pasa a ser la pantalla de Inicio dentro del shell: saludo con nombre y clínica, y tarjetas de atajo a **Nuevo paciente**, **Buscar paciente** (`/patients` con el foco en el buscador) y, si `canAccess(branding, role)`, **Personalizar marca**. Los atajos se derivan de las secciones (D1) para que el de Marca no se muestre a quien no puede entrar. El cierre de sesión sale del Inicio: está en la barra.

### D8. Listado de pacientes con estado en la URL

- `PatientList` lee y escribe `q`, `pagina` e `inactivos` con `useSearchParams` (`replace: true` al tipear, para no llenar el historial).
- Cada vez que el listado se muestra, guarda su URL completa en `sessionStorage` (`odr-patients-list`). Los enlaces "volver al listado" (ficha, formulario, "paciente no encontrado") usan `patientsListPath()`, que devuelve esa URL o `/patients`. `sessionStorage` es por pestaña, así que dos pestañas no se pisan.
- Se borra en el logout (junto a la caché de marca), para que otra persona en el mismo equipo no herede la búsqueda.
- Las pantallas de Historia, odontograma y consultas vuelven a la **ficha**, no al listado: no cambian.

### D9. Diseño visual

Superficie nueva de UI: el trabajo visual se hace con el skill `frontend-design` (reparto acordado: superficie nueva → `frontend-design`; reglas → `ui-ux-pro-max`; auditoría de pantallas existentes → `impeccable`). Se respetan los tokens existentes (sin colores literales: lo vigila `noLiteralColors.test.ts`) y el tema de la clínica, que la barra y la cabecera deben reflejar.

## Risks / Trade-offs

- **Rutas reordenadas** → un error de anidamiento rompe el acceso a una pantalla. Mitigación: tests de routing por sección (con y sin rol) y el e2e de navegación.
- **`PageContainer` deja de dar `min-h-screen`** → una pantalla que contaba con el alto completo (centrados verticales) puede verse distinta. Mitigación: `PatientNotFound` y similares se revisan en la pasada visual.
- **`sessionStorage` puede fallar** (modo privado estricto) → "volver" cae a `/patients` sin filtros; nunca rompe.
- **Tablet a 640–1023 px** recorta el contenido a ~1023−64 px; con `PageContainer` de 1024 px de máximo no hay desborde, pero se verifica en el e2e a 768 px.

## Migration Plan

Solo frontend. Merge = despliegue. Rollback = revertir el PR (vuelven el `HomeScreen` anterior y las rutas sin shell).
